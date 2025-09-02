package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.commons.model.UnitType
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.PerformanceReport
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.domain.TimeBucket
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class PerformanceReportDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<PerformanceReport> {
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<PerformanceReport>? = withSuspendingSpan {
        if (!input.dataConfiguration.reports.performance.enabled) return@withSuspendingSpan null

        val previousData = getPreviousData(input)
        input.interval
            .generateBucketedData(previousData) { timeBucket, previous ->
                getNextPerformanceReport(input, timeBucket, previous)
            }
            .let(::ByBucket)
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<PerformanceReport>,
    ): ByBucket<PerformanceReport>? = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<PerformanceReport> ->
            val inputSize = inputBuckets.size.toBigDecimal()

            val currentInvestment = inputBuckets.sumOf { it.currentInvestment }.divide(inputSize, MathContext.DECIMAL64)
            val currentProfit = inputBuckets.sumOf { it.currentProfit }.divide(inputSize, MathContext.DECIMAL64)
            val averageCurrencyValue =
                inputBuckets.sumOf { it.totalAssetsValue }.divide(currentInvestment, MathContext.DECIMAL64)

            val lastBucket = inputBuckets.last()

            PerformanceReport(
                totalAssetsValue = lastBucket.totalAssetsValue + currentInvestment + currentProfit,
                totalCurrencyValue = averageCurrencyValue,
                totalInvestment = lastBucket.totalInvestment + currentInvestment,
                currentInvestment = currentInvestment,
                totalProfit = lastBucket.totalProfit + currentProfit,
                currentProfit = currentProfit,
                investmentsByCurrency = emptyMap(),
                valueByCurrency = emptyMap(),
                assetsBySymbol = emptyMap(),
            )
        }
            .let { ByBucket(it) }
    }

    private suspend fun getPreviousData(input: ReportDataResolverInput): PerformanceReport {
        val previousRecords = input.recordStore.getPreviousRecords()
        val investmentByCurrency = extractInvestmentByCurrency(previousRecords)
        val valueByCurrency = extractValueByCurrency(previousRecords)
        val assetsBySymbol = extractAssetsBySymbol(previousRecords)

        return aggregatePerformanceReport(
            userId = input.userId,
            date = input.interval.getPreviousLastDay(),
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = BigDecimal.ZERO,
            valueByCurrency = valueByCurrency,
            previousInvestmentByCurrency = emptyMap(),
            currentInvestmentByCurrency = investmentByCurrency,
            previousAssetsBySymbol = emptyMap(),
            currentAssetsBySymbol = assetsBySymbol,
        )
    }

    private suspend fun getNextPerformanceReport(
        input: ReportDataResolverInput,
        timeBucket: TimeBucket,
        previous: PerformanceReport,
    ): PerformanceReport {
        val bucketRecords = input.recordStore.getBucketRecords(timeBucket)
        val bucketInvestmentByCurrency = extractInvestmentByCurrency(bucketRecords)
        val bucketValueByCurrency = extractValueByCurrency(bucketRecords)
        val bucketAssetsBySymbol = extractAssetsBySymbol(bucketRecords)

        return aggregatePerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = previous.totalProfit,
            valueByCurrency = mergeMaps(previous.valueByCurrency, bucketValueByCurrency),
            previousInvestmentByCurrency = previous.investmentsByCurrency,
            currentInvestmentByCurrency = bucketInvestmentByCurrency,
            previousAssetsBySymbol = previous.assetsBySymbol,
            currentAssetsBySymbol = bucketAssetsBySymbol,
        )
    }

    private fun extractValueByCurrency(records: List<ReportRecord>): Map<Currency, BigDecimal> {
        val groupBy = records
            .filter { it.unit.type == UnitType.CURRENCY }
            .groupBy { it.unit as Currency }
            .mapValues { (_, records) -> records.sumOf { it.amount } }
        return groupBy
    }

    private fun extractAssetsBySymbol(records: List<ReportRecord>): Map<Symbol, BigDecimal> =
        records
            .filter { it.isPositionBuy() }
            .groupBy { it.unit as Symbol }
            .mapValues { (symbol, records) -> records.sumOf { it.amount } }

    private fun extractInvestmentByCurrency(records: List<ReportRecord>): Map<Currency, BigDecimal> =
        records
            .filter { it.isPositionCost() }
            .groupBy { it.unit as Currency }
            .mapValues { (unit, records) -> records.sumOf { it.amount } }

    private suspend fun aggregatePerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        previousProfit: BigDecimal,
        valueByCurrency: Map<Currency, BigDecimal>,
        previousInvestmentByCurrency: Map<Currency, BigDecimal>,
        currentInvestmentByCurrency: Map<Currency, BigDecimal>,
        previousAssetsBySymbol: Map<Symbol, BigDecimal>,
        currentAssetsBySymbol: Map<Symbol, BigDecimal>,
    ): PerformanceReport {
        val currentInvestment = calculateInvestment(userId, date, targetCurrency, currentInvestmentByCurrency)
        val totalCurrencyValue = calculateCurrenciesValue(userId, date, targetCurrency, valueByCurrency)

        val totalInvestmentByCurrency = mergeMaps(previousInvestmentByCurrency, currentInvestmentByCurrency)
        val totalInvestment = calculateInvestment(userId, date, targetCurrency, totalInvestmentByCurrency)

        val totalAssetsBySymbol = mergeMaps(previousAssetsBySymbol, currentAssetsBySymbol)
        val totalAssetsValue = calculateAssetsValue(userId, date, targetCurrency, totalAssetsBySymbol)

        return PerformanceReport(
            totalAssetsValue = totalAssetsValue,
            totalCurrencyValue = totalCurrencyValue,
            totalInvestment = totalInvestment,
            totalProfit = totalAssetsValue - totalInvestment,
            currentInvestment = currentInvestment,
            currentProfit = totalAssetsValue - totalInvestment - previousProfit,
            investmentsByCurrency = totalInvestmentByCurrency,
            valueByCurrency = valueByCurrency,
            assetsBySymbol = totalAssetsBySymbol,
        )
    }

    private suspend fun calculateInvestment(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        investmentByCurrency: Map<Currency, BigDecimal>,
    ): BigDecimal {
        return investmentByCurrency
            .map { (unit, value) ->
                value * conversionRateService.getRate(userId, date, unit, targetCurrency)
            }
            .sumOf { it }
            .negate()
    }

    private suspend fun calculateAssetsValue(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        assetsBySymbol: Map<Symbol, BigDecimal>,
    ): BigDecimal {
        return assetsBySymbol
            .map { (unit, value) ->
                value * conversionRateService.getRate(userId, date, unit, targetCurrency)
            }
            .sumOf { it }
    }

    private suspend fun calculateCurrenciesValue(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        valueByCurrency: Map<Currency, BigDecimal>,
    ): BigDecimal {
        return valueByCurrency
            .map { (unit, value) ->
                value * conversionRateService.getRate(userId, date, unit, targetCurrency)
            }
            .sumOf { it }
    }

    private fun <T : FinancialUnit> mergeMaps(one: Map<T, BigDecimal>, two: Map<T, BigDecimal>): Map<T, BigDecimal> {
        return sequenceOf<Map<T, BigDecimal>>(one, two)
            .flatMap<Map<T, BigDecimal>, Pair<T, BigDecimal>> { it.entries.map<Map.Entry<T, BigDecimal>, Pair<T, BigDecimal>> { entry -> entry.key to entry.value } }
            .groupBy<Pair<T, BigDecimal>, T> { (key, value) -> key }
            .mapValues<T, List<Pair<T, BigDecimal>>, BigDecimal> { (_, values) -> values.sumOf<Pair<T, BigDecimal>> { it.second } }
    }

    private fun ReportRecord.isPositionCost(): Boolean {
        // TODO(Johann) not good. this will also match on money withdrawal. hmm, but couldn't I just add up everything by Currency and Symbol?
        return unit.type == UnitType.CURRENCY && amount < BigDecimal.ZERO
    }

    private fun ReportRecord.isPositionBuy(): Boolean {
        return unit.type == UnitType.SYMBOL && amount > BigDecimal.ZERO
    }
}
