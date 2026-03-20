package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime

data class AnalyticsValueAggregate(
    val dateTime: LocalDateTime,
    val sum: BigDecimal,
)
