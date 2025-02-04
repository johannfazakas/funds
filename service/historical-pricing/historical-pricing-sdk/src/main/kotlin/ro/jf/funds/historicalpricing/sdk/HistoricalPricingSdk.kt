package ro.jf.funds.historicalpricing.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.commons.web.toApiException
import ro.jf.funds.historicalpricing.api.model.*
import java.util.*

private val log = logger { }

private const val LOCALHOST_BASE_URL = "http://localhost:5231"

class HistoricalPricingSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val blockingHttpClient = OkHttpClient()

    suspend fun convert(userId: UUID, request: ConversionsRequest): ConversionsResponse {
        val response = httpClient.post("${baseUrl}/funds-api/historical-pricing/v1/conversions") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on conversion: $response" }
            throw response.toApiException()
        }
        return response.body()
    }

    @Deprecated("Use convert instead")
    fun convertInstrument(instrument: Instrument, currency: Currency, date: LocalDate): HistoricalPrice {
        val historicalPrices = convertInstrument(instrument, currency, listOf(date))
        require(historicalPrices.size == 1) { "Expected 1 historical price, but got ${historicalPrices.size}" }
        return historicalPrices.first()
    }

    @Deprecated("Use convert instead")
    fun convertInstrument(instrument: Instrument, currency: Currency, dates: List<LocalDate>): List<HistoricalPrice> {
        val request = InstrumentConversionRequest(instrument, currency, dates)
        val response: InstrumentConversionResponse = blockingHttpClient.newCall(
            Request.Builder()
                .url("$baseUrl/api/historical-pricing/instruments/convert")
                .post(Json.encodeToString(request).toRequestBody("application/json".toMediaType()))
                .build()
        )
            .execute()
            .let { it.body?.string()?.let(Json.Default::decodeFromString) ?: error("Empty response body") }
        return response.historicalPrices
    }

    @Deprecated("Use convert instead")
    fun convertCurrency(sourceCurrency: Currency, targetCurrency: Currency, date: LocalDate): HistoricalPrice {
        val historicalPrices = convertCurrency(sourceCurrency, targetCurrency, listOf(date))
        require(historicalPrices.size == 1) { "Expected 1 historical price, but got ${historicalPrices.size}" }
        return historicalPrices.first()
    }

    @Deprecated("Use convert instead")
    fun convertCurrency(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<HistoricalPrice> {
        val request = CurrencyConversionRequest(sourceCurrency, targetCurrency, dates)
        val response: CurrencyConversionResponse = blockingHttpClient.newCall(
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
