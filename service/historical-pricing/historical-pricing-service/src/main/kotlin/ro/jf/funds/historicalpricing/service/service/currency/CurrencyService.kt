package ro.jf.funds.historicalpricing.service.service.currency

import ro.jf.funds.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.funds.historicalpricing.api.model.CurrencyConversionResponse
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice

class CurrencyService(
    private val currencyConverter: CurrencyConverter,
    private val currencyPairHistoricalPriceRepository: CurrencyPairHistoricalPriceRepository
) {
    suspend fun convert(request: CurrencyConversionRequest): CurrencyConversionResponse {
        val (sourceCurrency, targetCurrency, dates) = request

        val storedHistoricalPricesByDate = currencyPairHistoricalPriceRepository
            .getHistoricalPrices(sourceCurrency.value, targetCurrency.value, dates)
            .map { HistoricalPrice(it.date, it.price) }
            .associateBy { it.date }

        val newConversionsByDate = currencyConverter
            .convert(sourceCurrency, targetCurrency, dates.filterNot { it in storedHistoricalPricesByDate.keys })
            .onEach {
                currencyPairHistoricalPriceRepository.saveHistoricalPrice(
                    CurrencyPairHistoricalPrice(sourceCurrency.value, targetCurrency.value, it.date, it.price)
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
