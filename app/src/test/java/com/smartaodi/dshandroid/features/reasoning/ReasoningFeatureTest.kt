package com.smartaodi.dshandroid.features.reasoning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningFeatureTest {
    @Test
    fun `separates reasoning and visible text deltas`() {
        val reasoning = ReasoningFeatureCodec.assistantDelta(
            JSONObject("""{"type":"assistant/chunk","data":{"chunk":{"type":"reasoning-delta","index":0,"text":"thinking"}}}"""),
        )!!
        val text = ReasoningFeatureCodec.assistantDelta(
            JSONObject("""{"type":"assistant/chunk","data":{"chunk":{"type":"text-delta","index":1,"text":"answer"}}}"""),
        )!!

        assertEquals(AssistantDeltaKind.Reasoning, reasoning.kind)
        assertEquals("thinking", reasoning.text)
        assertEquals(AssistantDeltaKind.Text, text.kind)
    }
}
