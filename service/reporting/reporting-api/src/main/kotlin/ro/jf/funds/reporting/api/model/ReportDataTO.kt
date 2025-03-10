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
    val granularInterval: GranularDateInterval,
    val data: List<ReportDataItemTO>,
)

@Serializable
data class ReportDataItemTO(
    val timeBucket: DateInterval,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal?,
    val value: ValueReportTO?,
    val groups: List<ReportDataGroupItemTO>?,
)

@Serializable
data class ReportDataGroupItemTO(
    val group: String,
    @Serializable(with = BigDecimalSerializer::class)
    val net: BigDecimal?,
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
