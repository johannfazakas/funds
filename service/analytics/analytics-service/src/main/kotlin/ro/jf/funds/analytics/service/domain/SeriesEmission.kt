package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime

sealed interface SeriesEmission {
    val value: SeriesSlice

    data class Previous(override val value: SeriesSlice) : SeriesEmission

    data class Bucket(val dateTime: LocalDateTime, override val value: SeriesSlice) : SeriesEmission
}

data class QueryBucketValue(
    val queryId: QueryId,
    val bucket: LocalDateTime,
    val values: Map<GroupKey, BigDecimal>,
)
