package ro.jf.finance.historicalpricing.service.domain.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.bk.historicalpricing.api.model.Currency
import ro.jf.bk.historicalpricing.api.model.HistoricalPrice

fun interface CurrencyConverter {
    suspend fun convert(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>
    ): List<HistoricalPrice>
}
