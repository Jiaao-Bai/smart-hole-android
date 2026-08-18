package com.smartaodi.dshandroid.features.status

import org.json.JSONObject

data class ApiBalance(
    val isAvailable: Boolean,
    val balances: List<CurrencyBalance>,
    val checkedAt: Long,
)

data class CurrencyBalance(
    val currency: String,
    val total: String,
)

object ConversationStatusCodec {
    fun balance(value: Any?): ApiBalance? {
        val root = value as? JSONObject ?: return null
        if (root.optString("status") != "available") return null
        val entries = root.optJSONArray("balances") ?: return null
        val balances = buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val currency = entry.optString("currency")
                val total = entry.optString("total")
                if ((currency == "CNY" || currency == "USD") && total.isNotBlank()) {
                    add(CurrencyBalance(currency, total))
                }
            }
        }
        if (balances.isEmpty()) return null
        return ApiBalance(
            isAvailable = root.optBoolean("isAvailable", false),
            balances = balances,
            checkedAt = root.optLong("checkedAt", 0L),
        )
    }
}
