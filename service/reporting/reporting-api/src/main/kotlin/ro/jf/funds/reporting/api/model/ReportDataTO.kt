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

// TODO(Johann-UP) might rename to ByGroupTO, which could also make room for a ByUnitTO for performance
@Serializable
data class GroupedTO<T : GroupDataTO>(val groups: List<T>) {
    operator fun get(group: String): T {
        return groups.first { it.group == group }
    }
}

interface GroupDataTO {
    val group: String
}

@Serializable
data class NetReportTO(
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal,
)

@Serializable
data class GroupNetReportTO(
    override val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal,
) : GroupDataTO

@Serializable
data class GroupedBudgetReportTO(
    override val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    var allocated: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val spent: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val left: BigDecimal,
) : GroupDataTO

@Serializable
data class ValueReportTO(
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
