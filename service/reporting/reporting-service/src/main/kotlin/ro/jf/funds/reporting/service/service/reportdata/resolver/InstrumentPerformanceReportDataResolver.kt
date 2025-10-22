package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class InstrumentPerformanceReportDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<ByInstrument<InstrumentPerformanceReport>> {
    // TODO(Johann-UP) review & refactor class
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<ByInstrument<InstrumentPerformanceReport>> {
        val previousData = getPreviousReport(input)
        return input.interval
            .generateBucketedData(previousData) { timeBucket, previous ->
                getNextReport(input, timeBucket, previous)
            }
    }

    override suspend fun forecast(input: ReportDataForecastInput<ByInstrument<InstrumentPerformanceReport>>): ByBucket<ByInstrument<InstrumentPerformanceReport>> {
        return input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ByInstrument<InstrumentPerformanceReport>> ->
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
                    InstrumentPerformanceReport(
                        instrument = symbol,
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

    private suspend fun getPreviousReport(input: ReportDataResolverInput): ByInstrument<InstrumentPerformanceReport> =
        aggregateInstrumentPerformanceReport(
            userId = input.userId,
            date = input.interval.getPreviousLastDay(),
            targetCurrency = input.dataConfiguration.currency,
            transactions = input.reportTransactionStore.getPreviousTransactions().toInvestmentTransactions(),
            previous = emptyMap()
        )

    private suspend fun getNextReport(
        input: ReportDataResolverInput, timeBucket: TimeBucket, previous: ByInstrument<InstrumentPerformanceReport>,
    ): ByInstrument<InstrumentPerformanceReport> =
        aggregateInstrumentPerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            transactions = input.reportTransactionStore.getBucketTransactions(timeBucket).toInvestmentTransactions(),
            previous = previous
        )

    private fun List<ReportTransaction>.toInvestmentTransactions(): List<InvestmentTransaction> =
        this
            .mapNotNull { it as? ReportTransaction.OpenPosition }
            .map { it.toInvestmentTransaction() }

    private fun ReportTransaction.OpenPosition.toInvestmentTransaction(): InvestmentTransaction {
        return InvestmentTransaction.OpenPosition(
            sourceCurrency = currencyRecord.unit as Currency,
            sourceAmount = currencyRecord.amount,
            targetInstrument = instrumentRecord.unit as Instrument,
            targetAmount = instrumentRecord.amount,
        )
    }

    private suspend fun aggregateInstrumentPerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        transactions: List<InvestmentTransaction>,
        previous: ByInstrument<InstrumentPerformanceReport>,
    ): ByInstrument<InstrumentPerformanceReport> {
        val currentCurrencyInvestment = extractCurrencyInvestment(transactions)
        val currentUnits = extractUnits(transactions)

        val symbols = currentUnits.keys + previous.keys
        return symbols.associateWith { symbol ->
            aggregateInstrumentPerformanceReport(
                instrument = symbol,
                userId = userId,
                date = date,
                targetCurrency = targetCurrency,
                currentUnits = currentUnits[symbol] ?: BigDecimal.ZERO,
                currentInvestment = currentCurrencyInvestment[symbol] ?: emptyMap(),
                previous = previous[symbol] ?: InstrumentPerformanceReport.Companion.zero(symbol),
            )
        }
    }

    private suspend fun aggregateInstrumentPerformanceReport(
        instrument: Instrument,
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        currentUnits: BigDecimal,
        currentInvestment: ByCurrency<BigDecimal>,
        previous: InstrumentPerformanceReport,
    ): InstrumentPerformanceReport {
        val totalUnits = previous.totalUnits + currentUnits
        val totalValue = totalUnits * conversionRateService.getRate(userId, date, instrument, targetCurrency)
        val totalCurrencyInvestment = previous.investmentByCurrency.merge(currentInvestment) { a, b -> a + b }
        val totalInvestment = calculateInvestment(userId, date, targetCurrency, totalCurrencyInvestment)
        val currentInvestment = calculateInvestment(userId, date, targetCurrency, currentInvestment)

        return InstrumentPerformanceReport(
            instrument = instrument,
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
    ): ByInstrument<ByCurrency<BigDecimal>> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetInstrument }
        .mapValues { (_, transactions) ->
            transactions
                .groupBy { it.sourceCurrency }
                .mapValues { (_, transactions) -> transactions.sumOf { it.sourceAmount } }
        }
        .toMap()

    private fun extractUnits(transactions: List<InvestmentTransaction>): ByInstrument<BigDecimal> = transactions
        .asSequence()
        .mapNotNull { it as? InvestmentTransaction.OpenPosition }
        .groupBy { it.targetInstrument }
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
            val targetInstrument: Instrument,
            val targetAmount: BigDecimal,
        ) : InvestmentTransaction()
    }
}