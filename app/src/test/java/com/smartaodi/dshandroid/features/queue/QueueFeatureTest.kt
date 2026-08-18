package com.smartaodi.dshandroid.features.queue

import com.smartaodi.dshandroid.protocol.DshServerEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueFeatureTest {
    @Test
    fun `decodes authoritative queue snapshot and placements`() {
        val event = DshServerEvent(
            rpcId = "rpc-1",
            method = "session/queue",
            payload = JSONObject(
                """{"sessionId":"session-1","items":[
                  {"id":"q1","placement":"queued","message":{"content":[{"type":"text","text":"稍后做"}]}},
                  {"id":"q2","placement":"steering","message":{"content":[{"type":"text","text":"先停一下"}]}},
                  {"id":"q3","placement":"context","message":{"content":[{"type":"text","text":"内部上下文"}]}}
                ]}""",
            ),
        )

        val state = QueueFeatureState(items = QueueFeatureCodec.snapshot(event, "session-1")!!)
        assertEquals(listOf("稍后做"), state.queued.map { it.text })
        assertEquals(listOf("先停一下"), state.steering.map { it.text })
        assertEquals(3, state.items.size)
        assertNull(QueueFeatureCodec.snapshot(event, "another-session"))
    }

    @Test
    fun `encodes text as official content block`() {
        val content = QueueFeatureCodec.textContent("hello")
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("hello", content.getJSONObject(0).getString("text"))
    }
}
