package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.FinancialUnit

class BucketedUnitAggregates(
    private val byBucket: Map<LocalDateTime, Map<FinancialUnit, BigDecimal>>,
) {
    val units: Set<FinancialUnit> = byBucket.values.flatMap { it.keys }.toSet()

    fun getBucket(bucketDateTime: LocalDateTime): Map<FinancialUnit, BigDecimal> =
        byBucket[bucketDateTime] ?: emptyMap()
}
