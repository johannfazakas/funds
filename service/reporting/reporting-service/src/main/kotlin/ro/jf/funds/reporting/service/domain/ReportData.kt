package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.reporting.api.model.GranularDateInterval
import java.math.BigDecimal
import java.util.*

data class ReportData(
    val reportViewId: UUID,
    val granularInterval: GranularDateInterval,
    val data: List<ReportDataBucket>,
)

sealed class ReportDataBucket {
    abstract val timeBucket: LocalDate
}

data class ExpenseReportDataBucket(
    override val timeBucket: LocalDate,
    val amount: BigDecimal,
) : ReportDataBucket()
