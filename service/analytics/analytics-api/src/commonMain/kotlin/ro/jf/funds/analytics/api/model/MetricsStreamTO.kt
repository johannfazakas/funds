@file:UseSerializers(BigDecimalSerializer::class)

package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class MetricsStreamBucketsTO(
    val granularity: TimeGranularity,
    val buckets: List<LocalDateTime>,
)

@Serializable
data class MetricsStreamValueTO(
    val queryId: String,
    val bucket: LocalDateTime,
    val values: Map<String, BigDecimal>,
)

@Serializable
data class MetricsStreamErrorTO(
    val message: String,
)
