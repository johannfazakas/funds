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

data class ReportDataBucket(
    val timeBucket: LocalDate,
    val amount: BigDecimal,
    val value: ValueReport,
)

data class ValueReport(
    val start: BigDecimal = BigDecimal.ZERO,
    val end: BigDecimal = BigDecimal.ZERO,
    val min: BigDecimal = BigDecimal.ZERO,
    val max: BigDecimal = BigDecimal.ZERO,
)
