package com.smartaodi.dshandroid.features.jobs

import com.smartaodi.dshandroid.protocol.DshServerEvent

data class JobView(
    val id: String,
    val kind: String,
    val label: String,
    val status: String,
    val detail: String?,
    val startedAt: Long,
    val finishedAt: Long?,
) {
    val active: Boolean get() = status == "running" || status == "stopping"
}

data class JobsFeatureState(val items: List<JobView> = emptyList()) {
    val activeCount: Int get() = items.count(JobView::active)
}

object JobsFeatureCodec {
    fun snapshot(event: DshServerEvent, selectedSessionId: String?): List<JobView>? {
        if (event.method != "session/jobs") return null
        if (event.payload.optString("sessionId") != selectedSessionId) return null
        val jobs = event.payload.optJSONArray("jobs") ?: return emptyList()
        return buildList {
            for (index in 0 until jobs.length()) {
                val job = jobs.optJSONObject(index) ?: continue
                val id = job.optString("id").takeIf(String::isNotBlank) ?: continue
                add(
                    JobView(
                        id = id,
                        kind = job.optString("kind"),
                        label = job.optString("label"),
                        status = job.optString("status"),
                        detail = job.optString("detail").takeIf(String::isNotBlank),
                        startedAt = job.optLong("startedAt", 0L),
                        finishedAt = job.optLong("finishedAt", -1L).takeIf { it >= 0L },
                    ),
                )
            }
        }
    }
}
