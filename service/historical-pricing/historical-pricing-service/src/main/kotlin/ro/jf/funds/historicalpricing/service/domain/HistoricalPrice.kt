package ro.jf.funds.historicalpricing.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import java.math.BigDecimal

data class HistoricalPrice(
    val source: FinancialUnit,
    val target: Currency,
    val date: LocalDate,
    val price: BigDecimal
)
