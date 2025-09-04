package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.UUID

data class ReportTransaction(
    val date: LocalDate,
    val records: List<ReportRecord>,
)

data class ReportRecord(
    val date: LocalDate,
    val fundId: UUID,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val labels: List<Label>,
)
