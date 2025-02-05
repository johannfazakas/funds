package ro.jf.funds.historicalpricing.service.service.currency

import ro.jf.funds.historicalpricing.api.model.CurrencyConversionRequest
import ro.jf.funds.historicalpricing.api.model.CurrencyConversionResponse
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice

class CurrencyService(
    private val currencyConverter: CurrencyConverter,
    private val currencyPairHistoricalPriceRepository: CurrencyPairHistoricalPriceRepository,
) {
    suspend fun convert(request: CurrencyConversionRequest): CurrencyConversionResponse {
        val (sourceCurrency, targetCurrency, dates) = request

        val storedHistoricalPricesByDate = currencyPairHistoricalPriceRepository
            .getHistoricalPrices(sourceCurrency, targetCurrency, dates)
            .map { HistoricalPrice(it.date, it.price) }
            .associateBy { it.date }

        val newConversionsByDate = dates
            .filterNot { it in storedHistoricalPricesByDate.keys }
            .takeIf { it.isNotEmpty() }
            ?.let { it -> currencyConverter.convert(sourceCurrency, targetCurrency, it) }
            ?.onEach {
                currencyPairHistoricalPriceRepository.saveHistoricalPrice(
                    CurrencyPairHistoricalPrice(sourceCurrency, targetCurrency, it.date, it.price)
                )
            }
            ?.associateBy { it.date }
            ?: emptyMap()

        return CurrencyConversionResponse(
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            historicalPrices = dates
                .mapNotNull { date -> storedHistoricalPricesByDate[date] ?: newConversionsByDate[date] }
        )
    }
}
