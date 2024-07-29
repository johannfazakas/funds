package ro.jf.finance.historicalpricing.service.domain.service.currency

import ro.jf.bk.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.bk.historicalpricing.api.model.CurrencyConversionResponse
import ro.jf.bk.historicalpricing.api.model.HistoricalPrice
import ro.jf.finance.historicalpricing.service.domain.model.CurrencyPairHistoricalPrice

class CurrencyService(
    private val currencyConverter: CurrencyConverter,
    private val currencyPairHistoricalPriceRepository: CurrencyPairHistoricalPriceRepository
) {
    suspend fun convert(request: CurrencyConversionRequest): CurrencyConversionResponse {
        val (sourceCurrency, targetCurrency, dates) = request

        val storedHistoricalPricesByDate = currencyPairHistoricalPriceRepository
            .getHistoricalPrices(sourceCurrency.name, targetCurrency.name, dates)
            .map { HistoricalPrice(it.date, it.price) }
            .associateBy { it.date }

        val newConversionsByDate = currencyConverter
            .convert(sourceCurrency, targetCurrency, dates.filterNot { it in storedHistoricalPricesByDate.keys })
            .onEach {
                currencyPairHistoricalPriceRepository.saveHistoricalPrice(
                    CurrencyPairHistoricalPrice(sourceCurrency.name, targetCurrency.name, it.date, it.price)
                )
            }
            .associateBy { it.date }

        return CurrencyConversionResponse(
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            historicalPrices = dates
                .mapNotNull { date -> storedHistoricalPricesByDate[date] ?: newConversionsByDate[date] }
        )
    }
}
