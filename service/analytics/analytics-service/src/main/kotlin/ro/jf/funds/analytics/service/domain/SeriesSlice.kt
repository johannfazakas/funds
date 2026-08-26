package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal

sealed interface SeriesSlice {

    data class Records(val records: List<AnalyticsRecord>) : SeriesSlice

    data class Amounts(val amounts: GroupedUnitAmounts) : SeriesSlice

    data class Positions(val positions: List<InvestmentPosition>) : SeriesSlice

    data class Scalars(val values: Map<GroupKey, BigDecimal>) : SeriesSlice {
        companion object {
            val EMPTY = Scalars(emptyMap())
        }
    }
}
