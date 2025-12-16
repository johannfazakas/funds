package ro.jf.funds.conversion.service.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.conversion.api.model.ConversionResponse

fun interface CurrencyConverter {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<ConversionResponse>
}
