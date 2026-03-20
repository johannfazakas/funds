package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class TimeGranularity {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
