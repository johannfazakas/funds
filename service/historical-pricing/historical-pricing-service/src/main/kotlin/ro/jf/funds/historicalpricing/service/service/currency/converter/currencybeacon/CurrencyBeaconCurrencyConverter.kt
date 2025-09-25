package ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import mu.KotlinLogging
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.model.CBConversion
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate

// TODO(Johann) how could this be injected in a safe way?
private const val CURRENCY_BEACON_API_KEY = "BEUseQ6C0HKAKXOeA5dZzsxmXV8HuRbL"
private val QUERY_PARAM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private val log = KotlinLogging.logger {}

class CurrencyBeaconCurrencyConverter(
    private val httpClient: HttpClient,
) : CurrencyConverter {

    override suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> = dates.map { date -> convertSafely(sourceCurrency, targetCurrency, date) }

    private suspend fun convertSafely(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
        attempt: Int = 0,
    ): ConversionResponse {
        try {
            return convert(sourceCurrency, targetCurrency, date)
        } catch (exception: Exception) {
            if (attempt < 3) {
                return convertSafely(sourceCurrency, targetCurrency, date.minus(1, DateTimeUnit.DAY), attempt + 1)
                    .copy(date = date)
            } else {
                log.warn { "Could not convert $sourceCurrency to $targetCurrency" }
                throw exception
            }
        }
    }

    private suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
    ): ConversionResponse {
        val price = httpClient.get("https://api.currencybeacon.com/v1/historical") {
            parameter("base", sourceCurrency.value)
            parameter("symbols", targetCurrency.value)
            parameter("date", date.toQueryParam())
            parameter("api_key", CURRENCY_BEACON_API_KEY)
        }
            .body<CBConversion>()
            .rates[targetCurrency.value] ?: error("No conversion rate found for $targetCurrency")
        return ConversionResponse(
            date = date,
            rate = price,
            sourceUnit = sourceCurrency,
            targetUnit = targetCurrency,
        )
    }

    private fun LocalDate.toQueryParam() =
        QUERY_PARAM_FORMATTER.format(JavaLocalDate.of(this.year, this.monthNumber, this.dayOfMonth))
}
