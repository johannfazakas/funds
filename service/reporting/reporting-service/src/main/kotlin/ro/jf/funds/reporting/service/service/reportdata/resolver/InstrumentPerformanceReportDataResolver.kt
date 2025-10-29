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
    // TODO(Johann-Easy) review & refactor class
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
            transactions = input.reportTransactionStore.getPreviousTransactions().toOpenPositions(),
            previous = emptyMap()
        )

    private suspend fun getNextReport(
        input: ReportDataResolverInput, timeBucket: TimeBucket, previous: ByInstrument<InstrumentPerformanceReport>,
    ): ByInstrument<InstrumentPerformanceReport> =
        aggregateInstrumentPerformanceReport(
            userId = input.userId,
            date = timeBucket.to,
            targetCurrency = input.dataConfiguration.currency,
            transactions = input.reportTransactionStore.getBucketTransactions(timeBucket).toOpenPositions(),
            previous = previous
        )

    private fun List<ReportTransaction>.toOpenPositions(): List<ReportTransaction.OpenPosition> =
        this.mapNotNull { it as? ReportTransaction.OpenPosition }

    private suspend fun aggregateInstrumentPerformanceReport(
        userId: UUID,
        date: LocalDate,
        targetCurrency: Currency,
        transactions: List<ReportTransaction.OpenPosition>,
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
        transactions: List<ReportTransaction.OpenPosition>,
    ): ByInstrument<ByCurrency<BigDecimal>> = transactions
        .asSequence()
        .groupBy { it.instrumentRecord.unit as Instrument }
        .mapValues { (_, transactions) ->
            transactions
                .groupBy { it.currencyRecord.unit as Currency }
                .mapValues { (_, transactions) -> transactions.sumOf { it.currencyRecord.amount } }
        }
        .toMap()

    private fun extractUnits(transactions: List<ReportTransaction.OpenPosition>): ByInstrument<BigDecimal> = transactions
        .asSequence()
        .groupBy { it.instrumentRecord.unit as Instrument }
        .map { (symbol, transactions) ->
            symbol to transactions.sumOf { it.instrumentRecord.amount }
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
}