package ro.jf.funds.historicalpricing.service.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionResponse

fun interface CurrencyConverter {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse>
}
