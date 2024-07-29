package ro.jf.finance.historicalpricing.service.infra.converter.currency.currencybeacon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.LocalDate
import ro.jf.bk.historicalpricing.api.model.Currency
import ro.jf.bk.historicalpricing.api.model.HistoricalPrice
import ro.jf.finance.historicalpricing.service.domain.service.currency.CurrencyConverter
import ro.jf.finance.historicalpricing.service.infra.converter.currency.currencybeacon.model.CBConversion
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate

private const val CURRENCY_BEACON_API_KEY = "Tvq2HYD17h6pLceGMPY0iL4VECzVcm3H"
private val QUERY_PARAM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class CurrencyBeaconCurrencyConverter(
    private val httpClient: HttpClient
) : CurrencyConverter {

    override suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>
    ): List<HistoricalPrice> = dates.map { date -> convert(sourceCurrency, targetCurrency, date) }

    private suspend fun convert(sourceCurrency: Currency, targetCurrency: Currency, date: LocalDate): HistoricalPrice {
        val price = httpClient.get("https://api.currencybeacon.com/v1/historical") {
            parameter("base", sourceCurrency)
            parameter("symbols", targetCurrency)
            parameter("date", date.toQueryParam())
            parameter("api_key", CURRENCY_BEACON_API_KEY)
        }
            .body<CBConversion>()
            .rates[targetCurrency.name] ?: error("No conversion rate found for $targetCurrency")
        return HistoricalPrice(
            date = date,
            price = price
        )
    }

    private fun LocalDate.toQueryParam() =
        QUERY_PARAM_FORMATTER.format(JavaLocalDate.of(this.year, this.monthNumber, this.dayOfMonth))
}
