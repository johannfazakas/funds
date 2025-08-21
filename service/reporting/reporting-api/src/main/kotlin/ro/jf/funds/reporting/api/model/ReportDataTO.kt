package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class ReportDataTO(
    @Serializable(with = UUIDSerializer::class)
    val viewId: UUID,
    val interval: ReportDataIntervalTO,
    val data: List<ReportDataItemTO>,
)

enum class BucketTypeTO {
    REAL,
    FORECAST
}

@Serializable
data class ReportDataItemTO(
    val timeBucket: DateIntervalTO,
    val bucketType: BucketTypeTO,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal? = null,
    val value: ValueReportTO? = null,
    val groupedNet: List<ReportDataGroupedNetItemTO>? = null,
    val groupedBudget: List<ReportDataGroupedBudgetItemTO>? = null,
    val performance: PerformanceReportTO? = null,
)

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
