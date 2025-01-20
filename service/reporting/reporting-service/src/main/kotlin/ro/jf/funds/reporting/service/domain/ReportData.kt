package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.reporting.api.model.GranularDateInterval
import java.math.BigDecimal
import java.util.*

sealed class ReportData {
    abstract val reportViewId: UUID
    abstract val granularInterval: GranularDateInterval
    abstract val data: List<ReportDataBucket>
}

sealed class ReportDataBucket {
    abstract val timeBucket: LocalDate
}

data class ExpenseReportData(
    override val reportViewId: UUID,
    override val granularInterval: GranularDateInterval,
    override val data: List<ExpenseReportDataBucket>,
) : ReportData()

data class ExpenseReportDataBucket(
    override val timeBucket: LocalDate,
    val amount: BigDecimal,
) : ReportDataBucket()
