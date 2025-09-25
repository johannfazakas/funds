package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ByCurrency
import ro.jf.funds.reporting.service.domain.BySymbol
import ro.jf.funds.reporting.service.domain.ReportTransaction
import ro.jf.funds.reporting.service.domain.TimeBucket
import ro.jf.funds.reporting.service.domain.UnitPerformanceReport
import ro.jf.funds.reporting.service.domain.merge
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.domain.ReportDataForecastInput
import ro.jf.funds.reporting.service.domain.ReportDataResolverInput
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID
import kotlin.collections.plus

class UnitPerformanceReportDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<BySymbol<UnitPerformanceReport>> {
    // TODO(Johann-UP) review & refactor class
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<BySymbol<UnitPerformanceReport>> {
        val previousData = getPreviousReport(input)
        return input.interval
            .generateBucketedData(previousData) { timeBucket, previous ->
                getNextReport(input, timeBucket, previous)
            }
    }

    override suspend fun forecast(input: ReportDataForecastInput<BySymbol<UnitPerformanceReport>>): ByBucket<BySymbol<UnitPerformanceReport>> {
        return input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<BySymbol<UnitPerformanceReport>> ->
            val inputSize = inputBuckets.size.toBigDecimal()
            val distinctSymbols = inputBuckets.flatMap { it.keys }.toSet()

            distinctSymbols
                .associateWith { symbol ->
                    val unitReports = inputBuckets.mapNotNull { it[symbol] }
                    val lastReport = unitReports.last()
                    val currentUnits = unitReports.sumOf { it.currentUnits }.divide(inputSize, MathContext.DECIMAL32)
                    val currentInvestment =
                        unitReports.sumOf { it.currentInvestment }.divide(inputSize, MathContext.DECIMAL64)
                    val currentProfit = unitReports.sumOf { it.currentProfit }.divide(inputSize, MathContext.DECIMAL64)
                    UnitPerformanceReport(
                        symbol = symbol,
                        totalUnits = lastReport.totalUnits + currentUnits,
                        currentUnits = currentUnits,
                        totalValue = lastReport.totalValue + currentInvestment + currentProfit,
                        totalInvestment = lastReport.totalInvestment + currentInvestment,
                        currentInvestment = currentInvestment,
                        totalProfit = lastReport.totalProfit + currentProfit,
                        currentProfit = currentProfit,
                        investmentByCurrency = emptyMap()
                    )
                }
        }
    }

    private suspend fun getPreviousReport(input: ReportDataResolverInput): BySymbol<UnitPerformanceReport> =
        aggregateUnitPerformanceReport(
            userId = input.userId,
            date = input.interval.getPreviousLastDay(),
            targetCurrency = input.dataConfiguration.currency,
            transactions = input.reportTransactionStore.getPreviousTransactions().toInvestmentTransactions(),
            previous = emptyMap()
        )

    private suspend fun getNextReport(
        input: ReportDataResolverInput, timeBucket: TimeBucket, previous: BySymbol<UnitPerformanceReport>,
    ): BySymbol<UnitPerformanceReport> =
        aggregateUnitPerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            transactions = input.reportTransactionStore.getBucketTransactions(timeBucket).toInvestmentTransactions(),
            previous = previous
        )

    private fun List<ReportTransaction>.toInvestmentTransactions(): List<InvestmentTransaction> =
        this.mapNotNull { it.toInvestmentTransaction() }

    private fun ReportTransaction.toInvestmentTransaction(): InvestmentTransaction? {
        val symbolRecord = this.records.singleOrNull { it.unit is Symbol }
        val currencyRecord = this.records.singleOrNull { it.unit is Currency }

        if (symbolRecord != null && currencyRecord != null) {
            if (symbolRecord.amount > BigDecimal.ZERO && currencyRecord.amount < BigDecimal.ZERO) {
                return InvestmentTransaction.OpenPosition(
                    sourceCurrency = currencyRecord.unit as Currency,
                    sourceAmount = currencyRecord.amount,
                    targetSymbol = symbolRecord.unit as Symbol,
                    targetAmount = symbolRecord.amount,
                )
            }
        }
        return null
    }

    private suspend fun aggregateUnitPerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        transactions: List<InvestmentTransaction>,
        previous: BySymbol<UnitPerformanceReport>,
    ): BySymbol<UnitPerformanceReport> {
        val currentCurrencyInvestment = extractCurrencyInvestment(transactions)
        val currentUnits = extractUnits(transactions)

        val symbols = currentUnits.keys + previous.keys
        return symbols.associateWith { symbol ->
            aggregateUnitPerformanceReport(
                symbol = symbol,
                userId = userId,
                date = date,
                targetCurrency = targetCurrency,
                currentUnits = currentUnits[symbol] ?: BigDecimal.ZERO,
                currentInvestment = currentCurrencyInvestment[symbol] ?: emptyMap(),
                previous = previous[symbol] ?: UnitPerformanceReport.Companion.zero(symbol),
            )
        }
    }

    private suspend fun aggregateUnitPerformanceReport(
        symbol: Symbol,
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        currentUnits: BigDecimal,
        currentInvestment: ByCurrency<BigDecimal>,
        previous: UnitPerformanceReport,
    ): UnitPerformanceReport {
        val totalUnits = previous.totalUnits + currentUnits
        val totalValue = totalUnits * conversionRateService.getRate(userId, date, symbol, targetCurrency)
        val totalCurrencyInvestment = previous.investmentByCurrency.merge(currentInvestment) { a, b -> a + b }
        val totalInvestment = calculateInvestment(userId, date, targetCurrency, totalCurrencyInvestment)
        val currentInvestment = calculateInvestment(userId, date, targetCurrency, currentInvestment)

        return UnitPerformanceReport(
            symbol = symbol,
            totalUnits = totalUnits,
            currentUnits = currentUnits,
            totalValue = totalValue,
            totalInvestment = totalInvestment,
            currentInvestment = currentInvestment,
            totalProfit = totalValue - totalInvestment,
            currentProfit = totalValue - totalInvestment - previous.totalProfit,
            investmentByCurrency = totalCurrencyInvestment,
        )
    }

    private fun extractCurrencyInvestment(
        transactions: List<InvestmentTransaction>,
    ): BySymbol<ByCurrency<BigDecimal>> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetSymbol }
        .mapValues { (_, transactions) ->
            transactions
                .groupBy { it.sourceCurrency }
                .mapValues { (_, transactions) -> transactions.sumOf { it.sourceAmount } }
        }
        .toMap()

    private fun extractUnits(transactions: List<InvestmentTransaction>): BySymbol<BigDecimal> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetSymbol }
        .map { (symbol, transactions) ->
            symbol to transactions.sumOf { it.targetAmount }
        }
        .toMap()

    private suspend fun calculateInvestment(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        investmentByCurrency: ByCurrency<BigDecimal>,
    ): BigDecimal = investmentByCurrency
        .map { (unit, value) ->
            value * conversionRateService.getRate(userId, date, unit, targetCurrency)
        }
        .sumOf { it }
        .negate()


    // TODO(Johann) shouldn't this way to handle things be also used in the simple PerformanceReportDataResolver?
    sealed class InvestmentTransaction {
        data class OpenPosition(
            val sourceCurrency: Currency,
            val sourceAmount: BigDecimal,
            val targetSymbol: Symbol,
            val targetAmount: BigDecimal,
        ) : InvestmentTransaction()
    }
}