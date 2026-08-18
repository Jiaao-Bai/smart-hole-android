package com.smartaodi.dshandroid.features

import com.smartaodi.dshandroid.protocol.ChatRole
import com.smartaodi.dshandroid.protocol.DshProtocol
import com.smartaodi.dshandroid.protocol.DshSurfaceAdapter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFeaturesTest {
    @Test
    fun `registry composes work metrics and nested subagent features`() {
        val rootSnapshot = DshSurfaceAdapter.projectionSnapshot(
            JSONObject(
                """{"asOfSeq":9,"values":{"goal":{"goal":{"objective":"Ship","phase":"active","maxGoalRounds":8},"roundsStarted":2},"todos":[{"content":"Test","status":"in_progress"}],"plan":{"active":true,"pending":false},"tokenUsage":{"uncachedInputTokens":10,"outputTokens":5,"cacheReadTokens":20,"cacheWriteTokens":1},"contextPressure":{"projectedTokens":1200,"contextWindow":64000},"sessionStats":{"turns":3,"steps":7,"llmMs":100,"toolMs":20}}}""",
            ),
        )
        val sessions = DshProtocol.sessions(
            JSONObject(
                """{"items":[
                  {"sessionId":"root","updatedAt":1,"agentPreset":"android","projections":{"values":{}}},
                  {"sessionId":"child","parentSessionId":"root","origin":"subagent","running":true,"updatedAt":2,"agentPreset":"android","projections":{"values":{"subagent":{"mode":"continuable","label":"Audit"},"subagentTiming":{"settledMs":500,"active":{"since":100,"through":600}},"tokenUsage":{"uncachedInputTokens":4,"outputTokens":2,"cacheReadTokens":0,"cacheWriteTokens":0}}}},
                  {"sessionId":"grandchild","parentSessionId":"child","origin":"subagent","running":false,"updatedAt":3,"agentPreset":"android","projections":{"values":{"subagent":{"mode":"one-shot","label":"Inspect"}}}}
                ]}""",
            ),
            agentPreset = "android",
        )

        val features = CoreSessionFeatureRegistry.decode(
            SessionFeatureContext("root", rootSnapshot, sessions),
        )

        assertEquals("Ship", features.work?.goal?.objective)
        assertTrue(features.work?.plan?.enabled == true)
        assertEquals(36L, features.metrics?.tokenUsage?.total)
        assertEquals(31L, features.metrics?.tokenUsage?.billedInputTokens)
        assertEquals(65, features.metrics?.tokenUsage?.cacheHitPercent)
        assertEquals(1200L, features.metrics?.context?.projectedTokens)
        val child = features.subagents?.roots?.single()
        assertEquals("Audit", child?.label)
        assertTrue(child?.running == true)
        assertEquals(1000L, child?.durationMs)
        assertEquals("Inspect", child?.children?.single()?.label)
    }

    @Test
    fun `cache hit is absent until input usage exists`() {
        assertEquals(
            null,
            TokenUsage(
                uncachedInputTokens = 0,
                outputTokens = 10,
                cacheReadTokens = 0,
                cacheWriteTokens = 0,
            ).cacheHitPercent,
        )
    }

    @Test
    fun `unknown projections remain harmless and produce no feature`() {
        val snapshot = DshSurfaceAdapter.projectionSnapshot(
            JSONObject("""{"asOfSeq":1,"values":{"futurePlugin":{"shape":"orb"}}}"""),
        )

        val features = CoreSessionFeatureRegistry.decode(
            SessionFeatureContext("root", snapshot, emptyList()),
        )

        assertFalse(features.hasVisibleContent)
    }

    @Test
    fun `conversation reducer merges live projections and replays streaming lifecycle`() {
        val projection = DshProtocol.serverEvent(
            """{"type":"server-request","rpcId":"p1","method":"session/projection","payload":{"sessionId":"root","key":"plan","seq":4,"value":{"active":false,"pending":true}}}""",
        )
        val chunk = DshProtocol.serverEvent(
            """{"type":"server-request","rpcId":"e1","method":"session/event","payload":{"sessionId":"root","event":{"type":"assistant/chunk","seq":5,"data":{"turn":1,"step":1,"chunk":{"type":"text-delta","text":"hello"}}}}}""",
        )
        val end = DshProtocol.serverEvent(
            """{"type":"server-request","rpcId":"e2","method":"session/event","payload":{"sessionId":"root","event":{"type":"turn/end","seq":6,"data":{"turn":1,"reason":{"kind":"completed"}}}}}""",
        )

        val planned = ConversationReducer.reduce(ConversationState(), projection, "root").state
        val streaming = ConversationReducer.reduce(planned, chunk, "root").state
        val ended = ConversationReducer.reduce(streaming, end, "root")

        assertTrue(planned.planMode)
        assertEquals("hello", streaming.messages.single().text)
        assertEquals(ChatRole.Assistant, streaming.messages.single().role)
        assertFalse(ended.state.promptSubmitting)
        assertTrue(ended.reloadSessionList)
        assertNotNull(ended.state.projections)
    }
}
