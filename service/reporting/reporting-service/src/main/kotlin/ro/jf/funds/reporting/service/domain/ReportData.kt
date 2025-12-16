package ro.jf.funds.reporting.service.domain

import com.benasher44.uuid.Uuid
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculationCommand
import java.math.BigDecimal

data class ReportData<T>(
    val reportViewId: Uuid,
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

data class PerformanceReport(
    val totalAssetsValue: BigDecimal,
    val totalCurrencyValue: BigDecimal,

    val totalInvestment: BigDecimal,
    val currentInvestment: BigDecimal,

    val totalProfit: BigDecimal,
    val currentProfit: BigDecimal,

    val investmentsByCurrency: Map<Currency, BigDecimal>,
    val valueByCurrency: Map<Currency, BigDecimal>,
    val assetsByInstrument: ByInstrument<BigDecimal>,
)

data class InstrumentPerformanceReport(
    val instrument: Instrument,

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
        fun zero(instrument: Instrument) = InstrumentPerformanceReport(
            instrument = instrument,
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

data class InterestRateReport(
    val totalInterestRate: BigDecimal,
    val currentInterestRate: BigDecimal,

    val assetsByInstrument: ByInstrument<BigDecimal>,
    val valuation: BigDecimal,
    val valuationDate: LocalDate,
    val positions: List<InterestRateCalculationCommand.Position>,
)

data class InstrumentInterestRateReport(
    val instrument: Instrument,

    val totalInterestRate: BigDecimal,
    val currentInterestRate: BigDecimal,

    val assets: BigDecimal,
    val valuation: BigDecimal,
    val valuationDate: LocalDate,
    val positions: List<InterestRateCalculationCommand.Position>,
) {
    companion object {
        fun zero(instrument: Instrument, valuationDate: LocalDate) = InstrumentInterestRateReport(
            instrument = instrument,
            totalInterestRate = BigDecimal.ZERO,
            currentInterestRate = BigDecimal.ZERO,
            assets = BigDecimal.ZERO,
            valuation = BigDecimal.ZERO,
            valuationDate = valuationDate,
            positions = emptyList()
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
