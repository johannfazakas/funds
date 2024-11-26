package ro.jf.funds.historicalpricing.service.domain.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice

fun interface CurrencyConverter {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<HistoricalPrice>
}
