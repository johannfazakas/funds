@file:UseSerializers(BigDecimalSerializer::class)

package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class MetricsReportTO(
    val granularity: TimeGranularity,
    val buckets: List<LocalDateTime>,
    val series: List<MetricSeriesTO>,
)

@Serializable
data class MetricSeriesTO(
    val queryId: String,
    val metric: MetricTO,
    val unit: MetricUnitTypeTO,
    val currency: Currency? = null,
    val groups: List<MetricSeriesGroupTO>,
)

@Serializable
data class MetricSeriesGroupTO(
    val groupKey: String,
    val values: List<BigDecimal>,
)
