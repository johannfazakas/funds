package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.FinancialUnit

data class UnitAmounts(
    private val byUnit: Map<FinancialUnit, BigDecimal> = emptyMap(),
) {
    val units: Set<FinancialUnit> get() = byUnit.keys
    val entries get() = byUnit.entries

    operator fun get(unit: FinancialUnit): BigDecimal = byUnit[unit] ?: BigDecimal.ZERO

    operator fun plus(other: UnitAmounts): UnitAmounts =
        UnitAmounts((units + other.units).associateWith { this[it] + other[it] })

    companion object {
        val EMPTY = UnitAmounts()
    }
}

class BucketedUnitAmounts(
    private val byBucket: Map<LocalDateTime, UnitAmounts>,
) {
    val units: Set<FinancialUnit> = byBucket.values.flatMap { it.units }.toSet()

    fun getBucket(bucketDateTime: LocalDateTime): UnitAmounts =
        byBucket[bucketDateTime] ?: UnitAmounts.EMPTY
}

class GroupedUnitAmounts(
    private val byGroup: Map<String, UnitAmounts>,
) {
    val units: Set<FinancialUnit> = byGroup.values.flatMap { it.units }.toSet()
    val groupKeys: Set<String> = byGroup.keys

    operator fun get(groupKey: String): UnitAmounts = byGroup[groupKey] ?: UnitAmounts.EMPTY

    companion object {
        val EMPTY = GroupedUnitAmounts(emptyMap())
    }
}

class BucketedGroupedUnitAmounts(
    private val byBucket: Map<LocalDateTime, Map<String, UnitAmounts>>,
) {
    val units: Set<FinancialUnit> = byBucket.values.flatMap { it.values.flatMap { u -> u.units } }.toSet()
    val groupKeys: Set<String> = byBucket.values.flatMap { it.keys }.toSet()

    fun getBucket(bucketDateTime: LocalDateTime): Map<String, UnitAmounts> =
        byBucket[bucketDateTime] ?: emptyMap()
}
