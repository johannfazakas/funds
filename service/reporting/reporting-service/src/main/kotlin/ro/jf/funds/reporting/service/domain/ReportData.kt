package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import java.math.BigDecimal
import java.util.*

data class ReportData<T>(
    val reportViewId: UUID,
    val interval: ReportDataInterval,
    val buckets: List<BucketData<T>>,
)

enum class BucketType {
    REAL,
    FORECAST
}

data class BucketData<D>(
    val timeBucket: TimeBucket,
    val bucketType: BucketType,
    val report: D,
)

data class NetReport(
    val net: BigDecimal,
)

// TODO(Johann) implement performance by unit. it could also be by group defined on unit
data class PerformanceReport(
    val totalAssetsValue: BigDecimal,
    val totalCurrencyValue: BigDecimal,

    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,

    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,

// TODO(Johann-performance-interest) review usage

//    val totalInterest: BigDecimal,
//    val currentInterest: BigDecimal,

    val investmentsByCurrency: Map<Currency, BigDecimal>,
    val valueByCurrency: Map<Currency, BigDecimal>,
    val assetsBySymbol: BySymbol<BigDecimal>,
    // TODO(Johann) could also add percentages
)

data class UnitPerformanceReport(
    val symbol: Symbol,

    val totalUnits: BigDecimal,
    val currentUnits: BigDecimal,

    val totalValue: BigDecimal,

    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,

    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,

    val investmentByCurrency: Map<Currency, BigDecimal>,
) {
    companion object {
        fun zero(symbol: Symbol) = UnitPerformanceReport(
            symbol,
            totalUnits = BigDecimal.ZERO,
            currentUnits = BigDecimal.ZERO,
            totalValue = BigDecimal.ZERO,
            totalInvestment = BigDecimal.ZERO,
            currentInvestment = BigDecimal.ZERO,
            totalProfit = BigDecimal.ZERO,
            currentProfit = BigDecimal.ZERO,
            investmentByCurrency = emptyMap()
        )
    }
}

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
