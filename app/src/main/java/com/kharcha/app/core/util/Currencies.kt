package com.kharcha.app.core.util

import java.util.Currency
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String, val symbol: String) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || code.contains(q, ignoreCase = true) || name.contains(q, ignoreCase = true) || symbol.contains(q, ignoreCase = true)
    }
}

/** The currencies in use around the world today. Names and symbols come from the phone itself. */
object Currencies {
    // Android also knows hundreds of retired currencies (the Andorran peseta, old Afghanis).
    // This is the list of the ones still in use.
    private const val ACTIVE =
        "AED AFN ALL AMD ANG AOA ARS AUD AWG AZN BAM BBD BDT BGN BHD BIF BMD BND BOB BRL BSD BTN BWP BYN BZD " +
            "CAD CDF CHF CLP CNY COP CRC CUP CVE CZK DJF DKK DOP DZD EGP ERN ETB EUR FJD FKP GBP GEL GHS GIP GMD " +
            "GNF GTQ GYD HKD HNL HTG HUF IDR ILS INR IQD IRR ISK JMD JOD JPY KES KGS KHR KMF KPW KRW KWD KYD KZT " +
            "LAK LBP LKR LRD LSL LYD MAD MDL MGA MKD MMK MNT MOP MRU MUR MVR MWK MXN MYR MZN NAD NGN NIO NOK NPR " +
            "NZD OMR PAB PEN PGK PHP PKR PLN PYG QAR RON RSD RUB RWF SAR SBD SCR SDG SEK SGD SHP SLE SOS SRD SSP " +
            "STN SVC SYP SZL THB TJS TMT TND TOP TRY TTD TWD TZS UAH UGX USD UYU UZS VES VND VUV WST XAF XCD XOF " +
            "XPF YER ZAR ZMW ZWL"

    val all: List<CurrencyInfo> by lazy {
        ACTIVE.split(' ').mapNotNull { code ->
            val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return@mapNotNull null
            CurrencyInfo(code, currency.getDisplayName(Locale.getDefault()), currency.getSymbol(Locale.getDefault()))
        }
    }

    private val byCode: Map<String, CurrencyInfo> by lazy { all.associateBy { it.code } }

    fun info(code: String): CurrencyInfo = byCode[code] ?: CurrencyInfo(code, code, code)

    fun name(code: String): String = info(code).name

    /** The currency of the country the phone is set to, or US dollars if that is unknown. */
    fun ofPhone(): String {
        val code = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
        return if (code != null && code in byCode) code else "USD"
    }
}
