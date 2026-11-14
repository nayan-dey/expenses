package com.kharcha.app.data

import com.kharcha.app.core.model.Rates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Currency exchange rates. This is the ONLY part of Kharcha that uses the internet: it
 * downloads one small public file of rates. Nothing about the person or their money is sent.
 *
 * Rates typed in by hand ("custom") always win over downloaded ones.
 */
class RatesRepository(private val file: JsonFile<Rates>, private val now: () -> Long) {

    val rates: StateFlow<Rates> get() = file.state

    /** True while a download is running. */
    val busy = MutableStateFlow(false)

    /** How many units of `to` one unit of `from` is worth, or null if nobody knows. */
    fun rate(from: String, to: String, rates: Rates = file.value): Double? {
        if (from == to) return 1.0
        rates.custom["$from>$to"]?.let { return it }
        rates.custom["$to>$from"]?.let { if (it > 0.0) return 1.0 / it }
        val fromPerBase = rates.perBase[from] ?: return null
        val toPerBase = rates.perBase[to] ?: return null
        if (fromPerBase <= 0.0) return null
        return toPerBase / fromPerBase
    }

    fun isCustom(from: String, to: String, rates: Rates = file.value): Boolean =
        rates.custom.containsKey("$from>$to") || rates.custom.containsKey("$to>$from")

    /** Sets a rate by hand, or removes it again with null. */
    fun setCustom(from: String, to: String, rate: Double?) {
        file.update { current ->
            val custom = current.custom.toMutableMap()
            custom.remove("$to>$from")
            if (rate == null || rate <= 0.0) custom.remove("$from>$to") else custom["$from>$to"] = rate
            current.copy(custom = custom)
        }
    }

    /** Downloads fresh rates if the saved ones are older than `maxAgeMs`. */
    suspend fun refreshIfOlderThan(maxAgeMs: Long): Boolean =
        if (now() - file.value.fetchedAt > maxAgeMs) refresh() else true

    /** Downloads fresh rates. Returns false when the phone is offline or both sources fail. */
    suspend fun refresh(): Boolean {
        if (busy.value) return false
        busy.value = true
        try {
            val fetched = withContext(Dispatchers.IO) {
                SOURCES.firstNotNullOfOrNull { source -> runCatching { source.fetch() }.getOrNull()?.takeIf { it.size > 20 }?.let { source.name to it } }
            } ?: return false
            val (source, perUsd) = fetched
            file.update { it.copy(base = "USD", fetchedAt = now(), perBase = perUsd + ("USD" to 1.0), source = source) }
            return true
        } finally {
            busy.value = false
        }
    }

    private class Source(val name: String, val url: String, val parse: (JsonObject) -> Map<String, Double>) {
        fun fetch(): Map<String, Double> {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                return parse(KharchaJson.parseToJsonElement(text).jsonObject)
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        fun numbers(node: JsonObject?): Map<String, Double> = node.orEmpty()
            .mapNotNull { (key, value) ->
                val number = runCatching { value.jsonPrimitive.doubleOrNull }.getOrNull()
                // Three-letter codes only: the first source also lists crypto coins.
                if (number != null && number > 0.0 && key.length == 3) key.uppercase() to number else null
            }
            .toMap()

        val SOURCES = listOf(
            // A free, open data set served from a public file CDN. No key, no account.
            Source(
                name = "fawazahmed0/exchange-api",
                url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
                parse = { root -> numbers(root["usd"]?.jsonObject) },
            ),
            // Fallback when the first one cannot be reached.
            Source(
                name = "open.er-api.com",
                url = "https://open.er-api.com/v6/latest/USD",
                parse = { root -> numbers(root["rates"]?.jsonObject) },
            ),
        )
    }
}
