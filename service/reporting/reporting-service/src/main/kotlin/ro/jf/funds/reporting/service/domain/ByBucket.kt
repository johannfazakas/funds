package ro.jf.funds.reporting.service.domain

import ro.jf.funds.reporting.api.model.DateInterval

data class ByBucket<T>(
    private val itemByBucket: Map<DateInterval, T>,
) {
    operator fun get(dateInterval: DateInterval): T? = itemByBucket[dateInterval]
}
