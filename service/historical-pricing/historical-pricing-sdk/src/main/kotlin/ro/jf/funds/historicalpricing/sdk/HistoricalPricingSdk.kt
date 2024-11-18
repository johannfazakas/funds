package ro.jf.funds.historicalpricing.sdk

import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ro.jf.funds.historicalpricing.api.model.*

private const val LOCALHOST_BASE_URL = "http://localhost:5231"

class HistoricalPricingSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL
) {
    private val httpClient = OkHttpClient()

    // TODO(Johann) remove this currency from the api
    fun convertInstrument(instrument: Instrument, currency: Currency, date: LocalDate): HistoricalPrice {
        val historicalPrices = convertInstrument(instrument, currency, listOf(date))
        require(historicalPrices.size == 1) { "Expected 1 historical price, but got ${historicalPrices.size}" }
        return historicalPrices.first()
    }

    fun convertInstrument(instrument: Instrument, currency: Currency, dates: List<LocalDate>): List<HistoricalPrice> {
        val request = InstrumentConversionRequest(instrument, currency, dates)
        val response: InstrumentConversionResponse = httpClient.newCall(
            Request.Builder()
                .url("$baseUrl/api/historical-pricing/instruments/convert")
                .post(Json.encodeToString(request).toRequestBody("application/json".toMediaType()))
                .build()
        )
            .execute()
            .let { it.body?.string()?.let(Json.Default::decodeFromString) ?: error("Empty response body") }
        return response.historicalPrices
    }

    fun convertCurrency(sourceCurrency: Currency, targetCurrency: Currency, date: LocalDate): HistoricalPrice {
        val historicalPrices = convertCurrency(sourceCurrency, targetCurrency, listOf(date))
        require(historicalPrices.size == 1) { "Expected 1 historical price, but got ${historicalPrices.size}" }
        return historicalPrices.first()
    }

    fun convertCurrency(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>
    ): List<HistoricalPrice> {
        val request = CurrencyConversionRequest(sourceCurrency, targetCurrency, dates)
        val response: CurrencyConversionResponse = httpClient.newCall(
            Request.Builder()
                .url("$baseUrl/api/historical-pricing/currencies/convert")
                .post(Json.encodeToString(request).toRequestBody("application/json".toMediaType()))
                .build()
        )
            .execute()
            .let { it.body?.string()?.let(Json.Default::decodeFromString) ?: error("Empty response body") }
        return response.historicalPrices
    }
}
