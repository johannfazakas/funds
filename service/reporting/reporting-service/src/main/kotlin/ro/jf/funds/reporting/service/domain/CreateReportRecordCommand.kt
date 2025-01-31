package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.*

data class CreateReportRecordCommand(
    val userId: UUID,
    val reportViewId: UUID,
    val date: LocalDate,
    val amount: BigDecimal,
    val labels: List<Label>,
)
