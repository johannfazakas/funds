package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class UnitPerformanceReportDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<BySymbol<UnitPerformanceReport>> {
    // TODO(Johann-UP) review & refactor class
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<BySymbol<UnitPerformanceReport>> {
        val previousData = getPreviousData(input)
        return input.interval
            .generateBucketedData(previousData) { timeBucket, previous ->
                getNextPerformanceReport(input, timeBucket, previous)
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
                        currentProfit = inputBuckets.mapNotNull { it[symbol] }.sumOf { it.currentProfit }
                            .divide(inputSize, MathContext.DECIMAL64),
                        investmentByCurrency = emptyMap()
                    )
                }
        }
    }

    private suspend fun getPreviousData(input: ReportDataResolverInput): BySymbol<UnitPerformanceReport> {
        val previousRecords = input.reportTransactionStore.getPreviousTransactions().toInvestmentTransactions()
        val investmentByCurrency = extractInvestmentByCurrency(previousRecords)
        val unitsBySymbol = extractAssetsBySymbol(previousRecords)
//        val valueByCurrency = extractValueByCurrency(previousRecords)

        return aggregateUnitPerformanceReport(
            userId = input.userId,
            date = input.interval.getPreviousLastDay(),
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = emptyMap(),
//            valueByCurrency = valueByCurrency,
            previousInvestmentByCurrency = emptyMap(),
            currentInvestmentByCurrency = investmentByCurrency,
            previousUnits = emptyMap(),
            currentUnits = unitsBySymbol,
        )
    }

    private suspend fun getNextPerformanceReport(
        input: ReportDataResolverInput,
        timeBucket: TimeBucket,
        previous: BySymbol<UnitPerformanceReport>,
    ): BySymbol<UnitPerformanceReport> {
        val bucketRecords = input.reportTransactionStore.getBucketTransactions(timeBucket).toInvestmentTransactions()
        val investmentByCurrency = extractInvestmentByCurrency(bucketRecords)
//        val bucketValueByCurrency = extractValueByCurrency(bucketRecords)
        val assetsBySymbol = extractAssetsBySymbol(bucketRecords)

        val previousInvestmentByCurrency = previous.mapValues { it.value.investmentByCurrency }
        val previousUnits = previous.mapValues { it.value.totalUnits }

        return aggregateUnitPerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            previousProfit = previous.mapValues { it.value.totalProfit },
//            valueByCurrency = mergeMaps(previous.valueByCurrency, bucketValueByCurrency),
            previousInvestmentByCurrency = previousInvestmentByCurrency,
            currentInvestmentByCurrency = investmentByCurrency,
            previousUnits = previousUnits,
            currentUnits = assetsBySymbol,
        )
    }

    // TODO(Johann-UP) maybe this could be split by symbol
    private suspend fun aggregateUnitPerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        // TODO(Johann-UP) this should be a Map<Symbol, BigDecimal> casting problems.
        previousProfit: Map<Symbol, BigDecimal>,
        previousInvestmentByCurrency: BySymbol<ByCurrency<BigDecimal>>,
        currentInvestmentByCurrency: BySymbol<ByCurrency<BigDecimal>>,
        previousUnits: Map<Symbol, BigDecimal>,
        cur(rentUnits: Map<Symbol, BigDecimal>,
    ): BySymbol<UnitPerformanceReport> {
        val currentInvestment = calculateInvestment(userId, date, targetCurrency, currentInvestmentByCurrency)
//        val totalCurrencyValue = calculateCurrenciesValue(userId, date, targetCurrency, valueByCurrency)

        val totalInvestmentByCurrency = mergeMaps1(previousInvestmentByCurrency, currentInvestmentByCurrency)
        val totalInvestment = calculateInvestment(userId, date, targetCurrency, totalInvestmentByCurrency)

        val totalUnitsBySymbol = mergeMaps2(previousUnits, currentUnits)
        val totalValue = calculateAssetsValue(userId, date, targetCurrency, totalUnitsBySymbol)

        return totalUnitsBySymbol.keys.associateWith { key ->
            val totalUnits = totalUnitsBySymbol[key] ?: BigDecimal.ZERO
            val currentUnits = currentUnits[key] ?: BigDecimal.ZERO
            val totalValue = totalValue[key] ?: BigDecimal.ZERO
            val totalInvestment = totalInvestment[key] ?: BigDecimal.ZERO
            val currentInvestment = currentInvestment[key] ?: BigDecimal.ZERO
            val previousProfit = previousProfit[key] ?: BigDecimal.ZERO
            val investmentByCurrency = totalInvestmentByCurrency[key] ?: emptyMap()

            UnitPerformanceReport(
                symbol = key,
                totalUnits = totalUnits,
                currentUnits = currentUnits,
                totalValue = totalValue,
                totalInvestment = totalInvestment,
                currentInvestment = currentInvestment,
                totalProfit = totalValue - totalInvestment,
                currentProfit = totalValue - totalInvestment - previousProfit,
                investmentByCurrency = investmentByCurrency
            )
        }
    }

    private suspend fun calculateInvestment(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        investmentByCurrency: Map<Symbol, Map<Currency, BigDecimal>>,
    ): Map<Symbol, BigDecimal> =
        investmentByCurrency.mapValues { (_, investmentByCurrency) ->
            investmentByCurrency
                .map { (unit, value) ->
                    value * conversionRateService.getRate(userId, date, unit, targetCurrency)
                }
                .sumOf { it }
                .negate()
        }

    // TODO(Johann-UP) rename these merge maps methods. maybe something generic could be extracted.
    private fun <T : FinancialUnit> mergeMaps1(
        one: Map<Symbol, Map<T, BigDecimal>>,
        two: Map<Symbol, Map<T, BigDecimal>>,
    ): Map<Symbol, Map<T, BigDecimal>> =
        sequenceOf(one, two)
            .flatMap { it.entries }
            .groupBy { it.key }
            .mapValues { (_, values) ->
                values
                    .flatMap { entry -> entry.value.entries }
                    .groupBy { it.key }
                    .mapValues { entry -> entry.value.sumOf { it.value } }
            }

    private fun <T : FinancialUnit> mergeMaps2(one: Map<T, BigDecimal>, two: Map<T, BigDecimal>): Map<T, BigDecimal> {
        return sequenceOf<Map<T, BigDecimal>>(one, two)
            .flatMap<Map<T, BigDecimal>, Pair<T, BigDecimal>> { it.entries.map<Map.Entry<T, BigDecimal>, Pair<T, BigDecimal>> { entry -> entry.key to entry.value } }
            .groupBy<Pair<T, BigDecimal>, T> { (key, _) -> key }
            .mapValues<T, List<Pair<T, BigDecimal>>, BigDecimal> { (_, values) -> values.sumOf<Pair<T, BigDecimal>> { it.second } }
    }

    private suspend fun calculateAssetsValue(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        assetsBySymbol: Map<Symbol, BigDecimal>,
    ): Map<Symbol, BigDecimal> =
        assetsBySymbol
            .mapValues { (unit, value) ->
                value * conversionRateService.getRate(userId, date, unit, targetCurrency)
            }

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

    // TODO(Johann-UP) shouldn't this way to handle things be also used in the simple PerformanceReportDataResolver?
    sealed class InvestmentTransaction {
        data class OpenPosition(
            val sourceCurrency: Currency,
            val sourceAmount: BigDecimal,
            val targetSymbol: Symbol,
            val targetAmount: BigDecimal,
        ) : InvestmentTransaction()
    }

    private fun extractInvestmentByCurrency(
        transactions: List<InvestmentTransaction>,
    ): Map<Symbol, Map<Currency, BigDecimal>> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetSymbol }
        .mapValues { (_, transactions) ->
            transactions
                .groupBy { it.sourceCurrency }
                .mapValues { (_, transactions) -> transactions.sumOf { it.sourceAmount } }
        }
        .toMap()

    private fun extractAssetsBySymbol(transactions: List<InvestmentTransaction>): Map<Symbol, BigDecimal> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetSymbol }
        .map { (symbol, transactions) ->
            symbol to transactions.sumOf { it.targetAmount }
        }
        .toMap()
}
