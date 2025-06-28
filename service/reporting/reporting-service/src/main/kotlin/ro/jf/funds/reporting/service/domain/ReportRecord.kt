package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.*

// TODO(Johann) investigate how comes that there are report records with 'income' label in 'expenses' fund
data class ReportRecord(
    val id: UUID,
    val userId: UUID,
    val reportViewId: UUID,
    val recordId: UUID,
    val date: LocalDate,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val labels: List<Label>,
)
