package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import java.math.BigDecimal
import java.util.*

data class ReportData(
    val reportViewId: UUID,
    val interval: ReportDataInterval,
    val data: List<BucketData<ReportDataAggregate>>,
)

enum class BucketType {
    REAL,
    FORECAST
}

data class BucketData<D>(
    val timeBucket: TimeBucket,
    val bucketType: BucketType,
    val aggregate: D,
)

data class ReportDataAggregate(
    val net: BigDecimal? = null,
    val value: ValueReport? = null,
    val groupedNet: ByGroup<BigDecimal>? = null,
    val groupedBudget: ByGroup<Budget>? = null,
    val performance: PerformanceReport? = null,
    // TODO(Johann) implement performance by unit. it could also be by group defined on unit
    val instrumentPerformance: ByUnit<PerformanceReport>? = null,
)

// TODO(Johann-48) add total value maybe, which is not equal to investment + profit
data class PerformanceReport(
    val totalAssetsValue: BigDecimal,
    val totalCurrencyValue: BigDecimal,

    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,

    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,

    val investmentsByCurrency: Map<Currency, BigDecimal>,
    val valueByCurrency: Map<Currency, BigDecimal>,
    val assetsBySymbol: Map<Symbol, BigDecimal>,
    // TODO(Johann) could also add percentages
)

data class ValueReport(
    val start: BigDecimal = BigDecimal.ZERO,
    val end: BigDecimal = BigDecimal.ZERO,
    val min: BigDecimal = BigDecimal.ZERO,
    val max: BigDecimal = BigDecimal.ZERO,
    val endAmountByUnit: ByUnit<BigDecimal>,
)

data class Budget(
    val allocated: BigDecimal,
    val spent: BigDecimal,
    val left: BigDecimal,
) {
    operator fun plus(other: Budget): Budget {
        return Budget(allocated + other.allocated, spent + other.spent, left + other.left)
    }
}
