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
    val data: List<ReportDataItemTO<T>>,
) {
    fun <O, R> merge(other: ReportDataTO<O>, combiner: (T, O) -> R): ReportDataTO<R> {
        require(viewId == other.viewId) { "Only reports on the same view can be combined" }
        require(interval == other.interval) { "Only reports on the same interval can be combined" }
        return ReportDataTO(
            this.viewId,
            this.interval,
            this.data.zip(other.data)
                .map { (thisItem, otherItem) ->
                    ReportDataItemTO(
                        thisItem.timeBucket,
                        thisItem.bucketType,
                        combiner(thisItem.data, otherItem.data)
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
data class ReportDataItemTO<T>(
    val timeBucket: DateIntervalTO,
    val bucketType: BucketTypeTO,
    val data: T,
)

@Serializable
data class ReportDataAggregateTO(
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal? = null,
    val value: ValueReportItemTO? = null,
    val groupedNet: List<ReportDataGroupedNetItemTO>? = null,
    val groupedBudget: List<ReportDataGroupedBudgetItemTO>? = null,
    val performance: PerformanceReportTO? = null,
)

@Serializable
data class ReportDataNetItemTO(
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal,
)

// TODO(Johann) remove all the nullables in these classes
@Serializable
data class ReportDataGroupedNetItemTO(
    val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal?,
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
