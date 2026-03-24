package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class AnalyticsReportTO(
    val granularity: TimeGranularity,
    val buckets: List<AnalyticsBucketTO>,
)

@Serializable
data class AnalyticsBucketTO(
    val dateTime: LocalDateTime,
    @Serializable(with = BigDecimalSerializer::class)
    val value: BigDecimal,
)
