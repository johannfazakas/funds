package ro.jf.funds.historicalpricing.service.service

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentService

class ConversionService(
    private val currencyService: CurrencyService,
    private val instrumentService: InstrumentService,
) {
    suspend fun convert(request: ConversionsRequest): ConversionsResponse {
        return request.conversions
            .groupBy { it.sourceUnit to it.targetUnit }
            .map { (currencyPair, requests) ->
                convert(currencyPair.first, currencyPair.second, requests.map { it.date })
            }
            .flatten()
            .let { ConversionsResponse(it) }
    }

    private suspend fun convert(
        sourceUnit: FinancialUnit,
        targetUnit: FinancialUnit,
        dates: List<LocalDate>,
    ): List<ConversionResponse> {
        return when (targetUnit) {
            is Currency -> {
                when (sourceUnit) {
                    is Currency -> currencyService.convert(sourceUnit, targetUnit, dates)
                    is Instrument -> instrumentService.convert(sourceUnit, targetUnit, dates)
                }
            }

            is Instrument -> throw HistoricalPricingExceptions.ConversionNotPermitted(sourceUnit, targetUnit)
        }
    }

    private fun ConversionRequest.isInstrumentConversion(): Boolean = sourceUnit is Instrument || targetUnit is Instrument
}
