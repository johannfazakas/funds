package ro.jf.funds.historicalpricing.service.service

import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService

class ConversionService(
    private val currencyService: CurrencyService,
) {
    suspend fun convert(request: ConversionsRequest): ConversionsResponse {
        return ConversionsResponse(
            emptyList()
        )
    }
}