package ro.jf.funds.reporting.service.domain

import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import java.math.BigDecimal
import java.util.*

data class ReportData(
    val reportViewId: UUID,
    val granularInterval: GranularDateInterval,
    val data: List<BucketData<ReportDataAggregate>>,
)

data class BucketData<D>(
    val timeBucket: DateInterval,
    val aggregate: D,
)

data class ReportDataAggregate(
    val net: BigDecimal?,
    val value: ValueReport?,
    val groupedNet: ByGroup<BigDecimal>?,
    val groupedBudget: ByGroup<Budget>?,
)

data class ValueReport(
    val start: BigDecimal = BigDecimal.ZERO,
    val end: BigDecimal = BigDecimal.ZERO,
    val min: BigDecimal = BigDecimal.ZERO,
    val max: BigDecimal = BigDecimal.ZERO,
    val endAmountByUnit: ByUnit<BigDecimal>,
)

data class Budget(
    val allocated: BigDecimal,
    val left: BigDecimal,
) {
    operator fun plus(other: Budget): Budget {
        return Budget(allocated + other.allocated, left + other.left)
    }
}
