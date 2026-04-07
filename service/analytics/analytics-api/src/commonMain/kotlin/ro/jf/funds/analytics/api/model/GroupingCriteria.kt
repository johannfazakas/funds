package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupingCriteria {
    CURRENCY,
    ACCOUNT,
    FUND,
}
