package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal

data class ReportRecord(
    val date: LocalDate,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val labels: List<Label>,
)
