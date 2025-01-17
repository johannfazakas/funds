package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.*

data class CreateReportRecordCommand(
    val userId: UUID,
    val reportViewId: UUID,
    val date: LocalDate,
    val amount: BigDecimal,
)
