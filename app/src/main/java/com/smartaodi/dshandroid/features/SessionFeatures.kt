package com.smartaodi.dshandroid.features

import com.smartaodi.dshandroid.protocol.SessionProjectionSnapshot
import com.smartaodi.dshandroid.protocol.SessionSummary
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Product-facing session capabilities decoded from DSH projections.
 *
 * The Host wire remains generic. Each module owns one coherent capability and
 * can be added or removed without teaching the screen about projection keys.
 */
sealed interface SessionFeature {
    data class Work(
        val goal: Goal? = null,
        val todos: List<Todo> = emptyList(),
        val plan: Plan? = null,
    ) : SessionFeature

    data class Metrics(
        val tokenUsage: TokenUsage? = null,
        val context: ContextPressure? = null,
        val stats: SessionStats? = null,
    ) : SessionFeature

    data class Subagents(val roots: List<Subagent>) : SessionFeature
}

data class Goal(
    val objective: String,
    val phase: String,
    val roundsStarted: Int,
    val maxGoalRounds: Int,
    val blockedReason: String? = null,
)

data class Todo(val content: String, val status: String)

data class Plan(val active: Boolean, val pending: Boolean) {
    val enabled: Boolean = if (pending) !active else active
}

data class TokenUsage(
    val uncachedInputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
) {
    val billedInputTokens: Long = uncachedInputTokens + cacheReadTokens + cacheWriteTokens
    val cacheHitPercent: Int? = billedInputTokens.takeIf { it > 0L }?.let {
        (cacheReadTokens.toDouble() / it * 100).roundToInt()
    }
    val total: Long = uncachedInputTokens + outputTokens + cacheReadTokens + cacheWriteTokens
}

data class ContextPressure(
    val pressureTokens: Long?,
    val projectedTokens: Long?,
    val contextWindow: Long?,
)

data class SessionStats(
    val turns: Int,
    val steps: Int,
    val llmMs: Long,
    val toolMs: Long,
)

data class Subagent(
    val sessionId: String,
    val label: String,
    val mode: String?,
    val running: Boolean,
    val durationMs: Long?,
    val tokenUsage: TokenUsage?,
    val children: List<Subagent>,
)

data class CoreSessionFeatures(
    val work: SessionFeature.Work? = null,
    val metrics: SessionFeature.Metrics? = null,
    val subagents: SessionFeature.Subagents? = null,
) {
    val hasVisibleContent: Boolean
        get() = work?.let { it.goal != null || it.todos.isNotEmpty() } == true ||
            metrics?.let { it.tokenUsage != null || it.context != null || it.stats != null } == true ||
            subagents?.roots?.isNotEmpty() == true
}

data class SessionFeatureContext(
    val sessionId: String?,
    val snapshot: SessionProjectionSnapshot?,
    val sessions: List<SessionSummary>,
)

fun interface SessionFeatureModule {
    fun decode(context: SessionFeatureContext): SessionFeature?
}

object CoreSessionFeatureRegistry {
    private val modules: List<SessionFeatureModule> = listOf(
        WorkFeatureModule,
        MetricsFeatureModule,
        SubagentFeatureModule,
    )

    fun decode(context: SessionFeatureContext): CoreSessionFeatures {
        var work: SessionFeature.Work? = null
        var metrics: SessionFeature.Metrics? = null
        var subagents: SessionFeature.Subagents? = null
        modules.mapNotNull { it.decode(context) }.forEach { feature ->
            when (feature) {
                is SessionFeature.Work -> work = feature
                is SessionFeature.Metrics -> metrics = feature
                is SessionFeature.Subagents -> subagents = feature
            }
        }
        return CoreSessionFeatures(work, metrics, subagents)
    }
}

object WorkFeatureModule : SessionFeatureModule {
    override fun decode(context: SessionFeatureContext): SessionFeature.Work? {
        val snapshot = context.snapshot ?: return null
        val goal = snapshot.jsonObject("goal")?.let { projection ->
            val value = projection.optJSONObject("goal") ?: return@let null
            val objective = value.stringOrNull("objective") ?: return@let null
            Goal(
                objective = objective,
                phase = value.stringOrNull("phase") ?: "unknown",
                roundsStarted = projection.optInt("roundsStarted", 0),
                maxGoalRounds = value.optInt("maxGoalRounds", 0),
                blockedReason = value.optJSONObject("blockedReason")?.stringOrNull("message"),
            )
        }
        val todos = snapshot.jsonArray("todos")?.mapObjects { item ->
            val content = item.stringOrNull("content") ?: return@mapObjects null
            val status = item.stringOrNull("status") ?: return@mapObjects null
            Todo(content, status)
        }.orEmpty()
        val planJson = snapshot.jsonObject("plan")
        val plan = planJson?.let {
            Plan(active = it.optBoolean("active", false), pending = it.optBoolean("pending", false))
        }
        return SessionFeature.Work(goal, todos, plan).takeIf {
            it.goal != null || it.todos.isNotEmpty() || it.plan != null
        }
    }
}

object MetricsFeatureModule : SessionFeatureModule {
    override fun decode(context: SessionFeatureContext): SessionFeature.Metrics? {
        val snapshot = context.snapshot ?: return null
        val usage = snapshot.jsonObject("tokenUsage")?.toTokenUsage()
        val contextPressure = snapshot.jsonObject("contextPressure")?.let {
            ContextPressure(
                pressureTokens = it.longOrNull("pressureTokens"),
                projectedTokens = it.longOrNull("projectedTokens"),
                contextWindow = it.longOrNull("contextWindow"),
            )
        }
        val stats = snapshot.jsonObject("sessionStats")?.let {
            SessionStats(
                turns = it.optInt("turns", 0),
                steps = it.optInt("steps", 0),
                llmMs = it.optLong("llmMs", 0L),
                toolMs = it.optLong("toolMs", 0L),
            )
        }
        return SessionFeature.Metrics(usage, contextPressure, stats).takeIf {
            it.tokenUsage != null || it.context != null || it.stats != null
        }
    }
}

object SubagentFeatureModule : SessionFeatureModule {
    override fun decode(context: SessionFeatureContext): SessionFeature.Subagents? {
        val parentId = context.sessionId ?: return null
        val byParent = context.sessions
            .filter { it.origin == "subagent" && it.parentSessionId != null }
            .groupBy { it.parentSessionId }

        fun node(session: SessionSummary, lineage: Set<String>): Subagent {
            val subagent = session.projections.jsonObject("subagent")
            val timing = session.projections.jsonObject("subagentTiming")
            val settledMs = timing?.optLong("settledMs", 0L) ?: 0L
            val active = timing?.optJSONObject("active")
            val activeMs = if (active != null) {
                (active.optLong("through", 0L) - active.optLong("since", 0L)).coerceAtLeast(0L)
            } else {
                0L
            }
            val duration = (settledMs + activeMs).takeIf { timing != null }
            val nextLineage = lineage + session.sessionId
            val children = byParent[session.sessionId].orEmpty()
                .filterNot { it.sessionId in nextLineage }
                .sortedBy(SessionSummary::updatedAt)
                .map { node(it, nextLineage) }
            return Subagent(
                sessionId = session.sessionId,
                label = subagent?.stringOrNull("label")
                    ?: session.title
                    ?: "子 Agent",
                mode = subagent?.stringOrNull("mode"),
                running = session.running,
                durationMs = duration,
                tokenUsage = session.projections.jsonObject("tokenUsage")?.toTokenUsage(),
                children = children,
            )
        }

        val roots = byParent[parentId].orEmpty()
            .sortedBy(SessionSummary::updatedAt)
            .map { node(it, setOf(parentId)) }
        return SessionFeature.Subagents(roots).takeIf { roots.isNotEmpty() }
    }
}

private fun SessionProjectionSnapshot?.jsonObject(key: String): JSONObject? {
    val json = this?.values?.get(key)?.json ?: return null
    if (json == "null") return null
    return runCatching { JSONObject(json) }.getOrNull()
}

private fun SessionProjectionSnapshot?.jsonArray(key: String): JSONArray? {
    val json = this?.values?.get(key)?.json ?: return null
    if (json == "null") return null
    return runCatching { JSONArray(json) }.getOrNull()
}

private fun JSONObject.toTokenUsage(): TokenUsage = TokenUsage(
    uncachedInputTokens = optLong("uncachedInputTokens", 0L),
    outputTokens = optLong("outputTokens", 0L),
    cacheReadTokens = optLong("cacheReadTokens", 0L),
    cacheWriteTokens = optLong("cacheWriteTokens", 0L),
)

private fun JSONObject.stringOrNull(key: String): String? = optString(key)
    .takeIf { has(key) && !isNull(key) && it.isNotBlank() }

private fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(transform)?.let(::add)
}
