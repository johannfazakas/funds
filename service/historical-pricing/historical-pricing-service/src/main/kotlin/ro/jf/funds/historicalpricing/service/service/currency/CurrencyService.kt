package ro.jf.funds.historicalpricing.service.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice

class CurrencyService(
    private val currencyConverter: CurrencyConverter,
    private val currencyPairHistoricalPriceRepository: CurrencyPairHistoricalPriceRepository,
) {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        // TODO(Johann) investigate why does it take so much when historical prices were not stored before
        val storedHistoricalPricesByDate = currencyPairHistoricalPriceRepository
            .getHistoricalPrices(sourceCurrency, targetCurrency, dates)
            .map { ConversionResponse(sourceCurrency, targetCurrency, it.date, it.price) }
        val storedHistoricalPricesDates = storedHistoricalPricesByDate.map { it.date }.toSet()

        val newConversionsByDate = dates
            .filterNot { it in storedHistoricalPricesDates }
            .takeIf { it.isNotEmpty() }
            ?.let { it -> currencyConverter.convert(sourceCurrency, targetCurrency, it) }
            ?.onEach {
                currencyPairHistoricalPriceRepository.saveHistoricalPrice(
                    CurrencyPairHistoricalPrice(sourceCurrency, targetCurrency, it.date, it.rate)
                )
            }
            ?: emptyList()

        return storedHistoricalPricesByDate + newConversionsByDate
    }
}
