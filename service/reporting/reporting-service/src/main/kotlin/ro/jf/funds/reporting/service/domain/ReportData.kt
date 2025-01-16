package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.reporting.api.model.GranularDateInterval
import java.math.BigDecimal
import java.util.*

data class ReportData(
    val reportViewId: UUID,
    val reportViewName: String,
    val fundId: UUID,
    val granularInterval: GranularDateInterval,
    val data: List<ReportDataBucket>,
)

sealed class ReportDataBucket {
    abstract val intervalStart: LocalDate
}

data class ExpenseReportDataBucket(
    override val intervalStart: LocalDate,
    val amount: BigDecimal,
) : ReportDataBucket()
