package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class MetricTO(val unit: MetricUnitTypeTO) {
    BALANCE(MetricUnitTypeTO.CURRENCY),
    NET_CHANGE(MetricUnitTypeTO.CURRENCY),
    TOTAL_INVESTMENT(MetricUnitTypeTO.CURRENCY),
    CURRENT_INVESTMENT(MetricUnitTypeTO.CURRENCY),
    TOTAL_INSTRUMENT_VALUE(MetricUnitTypeTO.CURRENCY),
    CURRENCY_VALUE(MetricUnitTypeTO.CURRENCY),
    TOTAL_PROFIT(MetricUnitTypeTO.CURRENCY),
    CURRENT_PROFIT(MetricUnitTypeTO.CURRENCY),
    TOTAL_INTEREST_RATE(MetricUnitTypeTO.PERCENTAGE),
    CURRENT_INTEREST_RATE(MetricUnitTypeTO.PERCENTAGE),
}
