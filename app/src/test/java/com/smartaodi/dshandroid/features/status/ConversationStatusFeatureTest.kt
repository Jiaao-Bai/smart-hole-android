package com.smartaodi.dshandroid.features.status

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationStatusFeatureTest {
    @Test
    fun `decodes sanitized balance totals`() {
        val value = JSONObject()
            .put("status", "available")
            .put("isAvailable", true)
            .put("checkedAt", 123L)
            .put(
                "balances",
                JSONArray().put(JSONObject().put("currency", "CNY").put("total", "12.34")),
            )

        assertEquals(
            ApiBalance(true, listOf(CurrencyBalance("CNY", "12.34")), 123L),
            ConversationStatusCodec.balance(value),
        )
    }

    @Test
    fun `ignores unavailable and malformed balances`() {
        assertNull(ConversationStatusCodec.balance(JSONObject().put("status", "unconfigured")))
        assertNull(
            ConversationStatusCodec.balance(
                JSONObject()
                    .put("status", "available")
                    .put("balances", JSONArray().put(JSONObject().put("currency", "BTC"))),
            ),
        )
    }
}
