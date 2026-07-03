@file:UseSerializers(BigDecimalSerializer::class)

package ro.jf.funds.analytics.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class AnalyticsReportTO<T>(
    val granularity: TimeGranularity,
    val buckets: List<AnalyticsBucketTO<T>>,
)

@Serializable
data class AnalyticsBucketTO<T>(
    val dateTime: LocalDateTime,
    val groups: List<AnalyticsGroupBucketTO<T>>,
)

@Serializable
data class AnalyticsGroupBucketTO<T>(
    val groupKey: String = "UNGROUPED",
    val value: T,
)
