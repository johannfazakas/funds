package ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import mu.KotlinLogging
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.model.CBConversion
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate

private val QUERY_PARAM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private const val MAX_ATTEMPTS = 5

private val log = KotlinLogging.logger {}

class CurrencyBeaconCurrencyConverter(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.currencybeacon.com",
    private val apiKey: String = "d92gJLQX3M8eGjo0ajqj089pImMs42Jm",
) : CurrencyConverter {

    override suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = coroutineScope {
        dates.map { date ->
            async { convertSafely(sourceCurrency, targetCurrency, date) }
        }.awaitAll()
    }

    private suspend fun convertSafely(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
        attempt: Int = 0,
    ): ConversionResponse {
        return try {
            convert(sourceCurrency, targetCurrency, date)
        } catch (priceNotFound: HistoricalPricingExceptions.HistoricalPriceNotFound) {
            if (attempt < MAX_ATTEMPTS) {
                convertSafely(sourceCurrency, targetCurrency, date.minus(1, DateTimeUnit.DAY), attempt + 1)
                    .copy(date = date)
            } else {
                log.warn(priceNotFound) { "Could not convert $sourceCurrency to $targetCurrency after ${attempt + 1} attempts" }
                throw priceNotFound
            }
        }
    }

    private suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
    ): ConversionResponse {
        val httpResponse = httpClient.get("$baseUrl/v1/historical") {
            parameter("base", sourceCurrency.value)
            parameter("symbols", targetCurrency.value)
            parameter("date", date.toQueryParam())
            parameter("api_key", apiKey)
        }

        val response = try {
            httpResponse.body<CBConversion>()
        } catch (exception: Exception) {
            val rawResponse = httpResponse.bodyAsText()
            log.error(exception) { "Failed to deserialize Currency Beacon response. Body: $rawResponse" }
            throw exception
        }

        if (!httpResponse.status.isSuccess()) {
            throwIntegrationException(
                httpResponse.status.value,
                response.meta?.errorDetail ?: "body: ${httpResponse.bodyAsText()}"
            )
        }

        val price = response.rates[targetCurrency.value]
            ?: throw HistoricalPricingExceptions.HistoricalPriceNotFound(
                sourceCurrency,
                targetCurrency,
                date
            )
        return ConversionResponse(
            date = date,
            rate = price,
            sourceUnit = sourceCurrency,
            targetUnit = targetCurrency,
        )
    }

    private fun throwIntegrationException(status: Int, errorDetail: String): Nothing {
        throw HistoricalPricingExceptions.HistoricalPricingIntegrationException(
            api = "currency_beacon",
            status = status,
            errorDetail = errorDetail
        )
    }

    private fun LocalDate.toQueryParam() =
        QUERY_PARAM_FORMATTER.format(JavaLocalDate.of(this.year, this.monthNumber, this.dayOfMonth))
}
