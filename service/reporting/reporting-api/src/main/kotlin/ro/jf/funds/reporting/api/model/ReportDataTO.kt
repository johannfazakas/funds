package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class ReportDataTO<T>(
    @Serializable(with = UUIDSerializer::class)
    val viewId: UUID,
    val interval: ReportDataIntervalTO,
    val timeBuckets: List<BucketDataTO<T>>,
) {
    fun <O, R> merge(other: ReportDataTO<O>, combiner: (T, O) -> R): ReportDataTO<R> {
        require(viewId == other.viewId) { "Only reports on the same view can be combined" }
        require(interval == other.interval) { "Only reports on the same interval can be combined" }
        return ReportDataTO(
            this.viewId,
            this.interval,
            this.timeBuckets.zip(other.timeBuckets)
                .map { (thisItem, otherItem) ->
                    BucketDataTO(
                        thisItem.timeBucket,
                        thisItem.bucketType,
                        combiner(thisItem.report, otherItem.report)
                    )
                },
        )
    }
}

enum class BucketTypeTO {
    REAL,
    FORECAST
}

@Serializable
data class BucketDataTO<T>(
    val timeBucket: DateIntervalTO,
    val bucketType: BucketTypeTO,
    val report: T,
)

// TODO(Johann) remove this
@Serializable
data class ReportDataAggregateTO(
    val net: NetReportTO? = null,
    val value: ValueReportItemTO? = null,
    // TODO(Johann) a generic group wrapper might help, might expose some methods
    val groupedNet: List<GroupNetReportTO>? = null,
    val groupedBudget: List<ReportDataGroupedBudgetItemTO>? = null,
    val performance: PerformanceReportTO? = null,
)

@Serializable
data class NetReportTO(
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal,
)

// TODO(Johann) remove all the nullables in these classes
@Serializable
data class GroupNetReportTO(
    val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal,
)

@Serializable
data class ReportDataGroupedBudgetItemTO(
    val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    var allocated: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    val spent: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    val left: BigDecimal?,
)

// TODO(Johann) names don't seem aligned very well
@Serializable
data class ValueReportItemTO(
    @Serializable(with = BigDecimalSerializer::class)
    val start: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val end: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val min: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val max: BigDecimal = BigDecimal.ZERO,
)

@Serializable
data class PerformanceReportTO(
    @Serializable(with = BigDecimalSerializer::class)
    val totalAssetsValue: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalCurrencyValue: BigDecimal,

    @Serializable(with = BigDecimalSerializer::class)
    val totalInvestment: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currentInvestment: BigDecimal,

    @Serializable(with = BigDecimalSerializer::class)
    val totalProfit: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currentProfit: BigDecimal,
)
