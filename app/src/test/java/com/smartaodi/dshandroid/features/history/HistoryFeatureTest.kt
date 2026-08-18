package com.smartaodi.dshandroid.features.history

import com.smartaodi.dshandroid.protocol.ChatMessage
import com.smartaodi.dshandroid.protocol.ChatRole
import com.smartaodi.dshandroid.protocol.SessionHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HistoryFeatureTest {
    @Test
    fun `prepends older page without duplicating boundary messages`() {
        val current = listOf(
            ChatMessage("event-10", ChatRole.User, "current"),
            ChatMessage("event-12", ChatRole.Assistant, "answer"),
        )
        val older = SessionHistory(
            messages = listOf(
                ChatMessage("event-2", ChatRole.User, "older"),
                ChatMessage("event-10", ChatRole.User, "current"),
            ),
            hasMore = false,
            oldestSeq = 1,
        )

        val merged = HistoryFeature.prepend(current, older)
        assertEquals(listOf("event-2", "event-10", "event-12"), merged.messages.map { it.id })
        assertFalse(merged.state.hasMore)
        assertEquals(1L, merged.state.oldestSeq)
    }
}
