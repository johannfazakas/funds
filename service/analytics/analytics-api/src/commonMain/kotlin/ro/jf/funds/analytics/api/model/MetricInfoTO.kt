package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
data class MetricInfoTO(
    val metric: MetricTO,
    val unit: MetricUnitTypeTO,
)
