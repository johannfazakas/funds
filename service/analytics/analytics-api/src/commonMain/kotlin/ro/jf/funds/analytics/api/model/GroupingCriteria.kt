package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupingCriteria {
    FINANCIAL_UNIT,
    ACCOUNT,
    FUND,
    CATEGORY,
}
