package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.*

data class ReportRecord(
    val id: UUID,
    val userId: UUID,
    val reportViewId: UUID,
    val recordId: UUID,
    val date: LocalDate,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val reportCurrencyAmount: BigDecimal,
    val labels: List<Label>,
)
