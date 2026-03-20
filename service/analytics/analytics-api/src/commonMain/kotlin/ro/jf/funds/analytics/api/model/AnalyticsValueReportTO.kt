package ro.jf.funds.analytics.api.model

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class AnalyticsValueReportRequestTO(
    val granularity: TimeGranularity,
    val from: LocalDateTime,
    val to: LocalDateTime,
    val fundIds: List<@Serializable(with = UuidSerializer::class) Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
)

@Serializable
data class AnalyticsValueReportTO(
    val granularity: TimeGranularity,
    val buckets: List<AnalyticsValueBucketTO>,
)

@Serializable
data class AnalyticsValueBucketTO(
    val dateTime: LocalDateTime,
    @Serializable(with = BigDecimalSerializer::class)
    val netChange: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal,
)
