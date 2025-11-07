package ro.jf.funds.historicalpricing.service.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.HistoricalPrice
import ro.jf.funds.historicalpricing.service.persistence.HistoricalPriceRepository

class CurrencyService(
    private val currencyConverter: CurrencyConverter,
    private val historicalPriceRepository: HistoricalPriceRepository,
) {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        val storedHistoricalPricesByDate = historicalPriceRepository
            .getHistoricalPrices(sourceCurrency, targetCurrency, dates)
            .map { ConversionResponse(sourceCurrency, targetCurrency, it.date, it.price) }
        val storedHistoricalPricesDates = storedHistoricalPricesByDate.map { it.date }.toSet()

        val newConversionsByDate = dates
            .filterNot { it in storedHistoricalPricesDates }
            .takeIf { it.isNotEmpty() }
            ?.let { currencyConverter.convert(sourceCurrency, targetCurrency, it) }
            ?.onEach {
                historicalPriceRepository.saveHistoricalPrice(
                    HistoricalPrice(sourceCurrency, targetCurrency, it.date, it.rate)
                )
            }
            ?: emptyList()

        return storedHistoricalPricesByDate + newConversionsByDate
    }
}
