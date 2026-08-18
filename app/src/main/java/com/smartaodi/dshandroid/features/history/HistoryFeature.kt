package com.smartaodi.dshandroid.features.history

import com.smartaodi.dshandroid.protocol.ChatMessage
import com.smartaodi.dshandroid.protocol.SessionHistory

data class HistoryFeatureState(
    val hasMore: Boolean = false,
    val oldestSeq: Long? = null,
    val loadingOlder: Boolean = false,
    val error: String? = null,
)

data class HistoryMerge(
    val messages: List<ChatMessage>,
    val state: HistoryFeatureState,
)

/** Pagination policy is isolated from transport and Compose. */
object HistoryFeature {
    fun replace(page: SessionHistory): HistoryMerge = HistoryMerge(
        messages = page.messages,
        state = HistoryFeatureState(hasMore = page.hasMore, oldestSeq = page.oldestSeq),
    )

    fun prepend(
        currentMessages: List<ChatMessage>,
        page: SessionHistory,
    ): HistoryMerge {
        val knownIds = currentMessages.asSequence().map(ChatMessage::id).toHashSet()
        return HistoryMerge(
            messages = page.messages.filterNot { it.id in knownIds } + currentMessages,
            state = HistoryFeatureState(hasMore = page.hasMore, oldestSeq = page.oldestSeq),
        )
    }
}
