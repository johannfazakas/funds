package ro.jf.funds.historicalpricing.service.service

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.historicalpricing.api.model.*
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService

class ConversionService(
    private val currencyService: CurrencyService,
) {
    suspend fun convert(request: ConversionsRequest): ConversionsResponse {
        request.conversions.firstOrNull { it.isSymbolConversion() }
            ?.let { throw HistoricalPricingExceptions.ConversionNotPermitted(it.sourceUnit, it.targetUnit) }

        return request.conversions
            .filter { it.sourceUnit is Currency && it.targetUnit is Currency }
            .groupBy { it.sourceUnit as Currency to it.targetUnit as Currency }
            .map { (currencyPair, requests) ->
                val dates = requests.map { it.date }
                dates to currencyService.convert(
                    CurrencyConversionRequest(
                        currencyPair.first,
                        currencyPair.second,
                        requests.map { it.date })
                )
            }
            .flatMap { (dates, conversionResponse) ->
                val historicalPricesByDate = conversionResponse.historicalPrices.associateBy { it.date }
                dates.map {
                    val historicalPrice =
                        historicalPricesByDate[it] ?: throw HistoricalPricingExceptions.HistoricalPriceNotFound(
                            conversionResponse.sourceCurrency, conversionResponse.targetCurrency, it
                        )
                    ConversionResponse(
                        conversionResponse.sourceCurrency,
                        conversionResponse.targetCurrency,
                        it,
                        historicalPrice.price
                    )
                }
            }
            .let { ConversionsResponse(it) }
    }

    private fun ConversionRequest.isSymbolConversion(): Boolean = sourceUnit is Symbol || targetUnit is Symbol
}
