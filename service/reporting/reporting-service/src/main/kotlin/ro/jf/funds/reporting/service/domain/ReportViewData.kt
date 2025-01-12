package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.reporting.api.model.GranularTimeInterval
import java.math.BigDecimal
import java.util.*

data class ReportViewData(
    val reportViewId: UUID,
    val granularInterval: GranularTimeInterval,
    val data: List<ReportDataBucket>,
)

sealed class ReportDataBucket {
    abstract val intervalStart: LocalDate
}

data class ExpenseReportDataBucket(
    override val intervalStart: LocalDate,
    val amount: BigDecimal,
) : ReportDataBucket()
