package com.smartaodi.dshandroid.ui.features.queue

import com.smartaodi.dshandroid.features.ContextPressure
import com.smartaodi.dshandroid.features.TokenUsage
import com.smartaodi.dshandroid.features.status.ApiBalance
import com.smartaodi.dshandroid.features.status.CurrencyBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueComposerTest {
    @Test
    fun `status places native DSH cache hit beside context usage`() {
        assertEquals(
            "上下文 12K/128K · 缓存命中 80% · 余额 ¥48.16",
            conversationStatusLabel(
                context = ContextPressure(
                    pressureTokens = null,
                    projectedTokens = 12_000,
                    contextWindow = 128_000,
                ),
                tokenUsage = TokenUsage(
                    uncachedInputTokens = 2_000,
                    outputTokens = 500,
                    cacheReadTokens = 8_000,
                    cacheWriteTokens = 0,
                ),
                apiBalance = ApiBalance(
                    isAvailable = true,
                    balances = listOf(CurrencyBalance("CNY", "48.16")),
                    checkedAt = 1,
                ),
            ),
        )
    }
}
