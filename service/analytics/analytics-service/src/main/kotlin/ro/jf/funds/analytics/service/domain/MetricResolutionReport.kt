package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime

data class ScalarSeries(
    val byBucket: Map<LocalDateTime, Map<GroupKey, BigDecimal>>,
) {
    val groupKeys: Set<GroupKey>
        get() = byBucket.values.flatMap { it.keys }.toSet().ifEmpty { setOf(GroupKey.Ungrouped) }

    operator fun get(bucket: LocalDateTime, groupKey: GroupKey): BigDecimal =
        byBucket[bucket]?.get(groupKey) ?: BigDecimal.ZERO
}

data class MetricResolutionReport(
    val buckets: List<LocalDateTime>,
    val series: Map<QueryId, ScalarSeries>,
) {
    operator fun get(queryId: QueryId): ScalarSeries = series.getValue(queryId)
}
