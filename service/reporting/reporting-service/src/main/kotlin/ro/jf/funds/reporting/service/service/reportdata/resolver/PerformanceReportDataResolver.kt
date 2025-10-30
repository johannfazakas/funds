package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.commons.model.UnitType
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.forecast.ForecastStrategy
import java.math.BigDecimal
import java.util.*

class PerformanceReportDataResolver(
    private val conversionRateService: ConversionRateService,
    private val forecastStrategy: ForecastStrategy,
) : ReportDataResolver<PerformanceReport> {
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<PerformanceReport> = withSuspendingSpan {
        val previousData = getPreviousData(input)
        input.interval
            .generateBucketedData(previousData) { timeBucket, previous ->
                getNextPerformanceReport(input, timeBucket, previous)
            }
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<PerformanceReport>,
    ): ByBucket<PerformanceReport> = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<PerformanceReport> ->
            val currentInvestment = forecastStrategy.forecastNext(inputBuckets.map { it.currentInvestment })
            val currentProfit = forecastStrategy.forecastNext(inputBuckets.map { it.currentProfit })
            val totalCurrencyValue = forecastStrategy.forecastNext(inputBuckets.map { it.totalCurrencyValue })

            val lastBucket = inputBuckets.last()

            PerformanceReport(
                totalAssetsValue = lastBucket.totalAssetsValue + currentInvestment + currentProfit,
                totalCurrencyValue = totalCurrencyValue,
                totalInvestment = lastBucket.totalInvestment + currentInvestment,
                currentInvestment = currentInvestment,
                totalProfit = lastBucket.totalProfit + currentProfit,
                currentProfit = currentProfit,
                investmentsByCurrency = emptyMap(),
                valueByCurrency = emptyMap(),
                assetsByInstrument = emptyMap(),
            )
        }
    }

    private suspend fun getPreviousData(input: ReportDataResolverInput): PerformanceReport {
        val fundId = input.reportView.fundId
        val previousRecords = input.reportTransactionStore.getPreviousTransactions()
        val investmentByCurrency = extractInvestmentByCurrency(previousRecords)
        val valueByCurrency = extractValueByCurrency(previousRecords, fundId)
        val assetsBySymbol = extractAssetsBySymbol(previousRecords, fundId)

        return aggregatePerformanceReport(
            userId = input.userId,
            date = input.interval.getPreviousLastDay(),
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = BigDecimal.ZERO,
            valueByCurrency = valueByCurrency,
            previousInvestmentByCurrency = emptyMap(),
            currentInvestmentByCurrency = investmentByCurrency,
            previousAssetsByInstrument = emptyMap(),
            currentAssetsByInstrument = assetsBySymbol,
        )
    }

    private suspend fun getNextPerformanceReport(
        input: ReportDataResolverInput,
        timeBucket: TimeBucket,
        previous: PerformanceReport,
    ): PerformanceReport {
        val fundId = input.reportView.fundId
        val bucketRecords = input.reportTransactionStore.getBucketTransactions(timeBucket)
        val bucketInvestmentByCurrency = extractInvestmentByCurrency(bucketRecords)
        val bucketValueByCurrency = extractValueByCurrency(bucketRecords, fundId)
        val bucketAssetsBySymbol = extractAssetsBySymbol(bucketRecords, fundId)

        return aggregatePerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = previous.totalProfit,
            valueByCurrency = mergeMaps(previous.valueByCurrency, bucketValueByCurrency),
            previousInvestmentByCurrency = previous.investmentsByCurrency,
            currentInvestmentByCurrency = bucketInvestmentByCurrency,
            previousAssetsByInstrument = previous.assetsByInstrument,
            currentAssetsByInstrument = bucketAssetsBySymbol,
        )
    }

    private fun extractValueByCurrency(transactions: List<ReportTransaction>, fundId: UUID): Map<Currency, BigDecimal> {
        val groupBy = transactions
            .flatMap { it.records }
            .filter { it.unit.type == UnitType.CURRENCY && it.fundId == fundId }
            .groupBy { it.unit as Currency }
            .mapValues { (_, records) -> records.sumOf { it.amount } }
        return groupBy
    }

    private fun extractAssetsBySymbol(
        transactions: List<ReportTransaction>,
        fundId: UUID,
    ): Map<Instrument, BigDecimal> =
        transactions
            .mapNotNull { it as? ReportTransaction.OpenPosition }
            .groupBy { it.instrumentRecord.unit as Instrument }
            .mapValues { (_, transactions) ->
                transactions
                    .map { it.instrumentRecord }
                    .filter { it.fundId == fundId }
                    .sumOf { it.amount }
            }

    private fun extractInvestmentByCurrency(records: List<ReportTransaction>): Map<Currency, BigDecimal> =
        records
            .mapNotNull { it as? ReportTransaction.OpenPosition }
            .groupBy { it.currencyRecord.unit as Currency }
            .mapValues { (_, transactions) ->
                transactions
                    .map { it.currencyRecord }
                    .sumOf { it.amount }
            }

    private suspend fun aggregatePerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        previousProfit: BigDecimal,
        valueByCurrency: Map<Currency, BigDecimal>,
        previousInvestmentByCurrency: Map<Currency, BigDecimal>,
        currentInvestmentByCurrency: Map<Currency, BigDecimal>,
        previousAssetsByInstrument: Map<Instrument, BigDecimal>,
        currentAssetsByInstrument: Map<Instrument, BigDecimal>,
    ): PerformanceReport {
        val currentInvestment = calculateInvestment(userId, date, targetCurrency, currentInvestmentByCurrency)
        val totalCurrencyValue = calculateCurrenciesValue(userId, date, targetCurrency, valueByCurrency)

        val totalInvestmentByCurrency = mergeMaps(previousInvestmentByCurrency, currentInvestmentByCurrency)
        val totalInvestment = calculateInvestment(userId, date, targetCurrency, totalInvestmentByCurrency)

        val totalAssetsBySymbol = mergeMaps(previousAssetsByInstrument, currentAssetsByInstrument)
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
            assetsByInstrument = totalAssetsBySymbol,
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
        assetsByInstrument: Map<Instrument, BigDecimal>,
    ): BigDecimal {
        return assetsByInstrument
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
            .groupBy<Pair<T, BigDecimal>, T> { (key, _) -> key }
            .mapValues<T, List<Pair<T, BigDecimal>>, BigDecimal> { (_, values) -> values.sumOf<Pair<T, BigDecimal>> { it.second } }
    }
}
