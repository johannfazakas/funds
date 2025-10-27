package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculationCommand
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculator
import java.math.BigDecimal
import java.math.MathContext

class InstrumentInterestRateReportResolver(
    private val conversionRateService: ConversionRateService,
    private val interestRateCalculator: InterestRateCalculator,
) : ReportDataResolver<ByInstrument<InstrumentInterestRateReport>> {

    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<ByInstrument<InstrumentInterestRateReport>> =
        withSuspendingSpan {
            input.interval
                .generateBucketedData(getPreviousInterestRateReport(input)) { timeBucket, previous ->
                    getNextInterestRateReport(input, timeBucket, previous)
                }
        }

    override suspend fun forecast(
        input: ReportDataForecastInput<ByInstrument<InstrumentInterestRateReport>>,
    ): ByBucket<ByInstrument<InstrumentInterestRateReport>> = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ByInstrument<InstrumentInterestRateReport>> ->
            val inputSize = inputBuckets.size.toBigDecimal()
            val distinctInstruments = inputBuckets.flatMap { it.keys }.toSet()

            distinctInstruments.associateWith { instrument ->
                val instrumentReports = inputBuckets.mapNotNull { it[instrument] }
                val lastReport = instrumentReports.last()
                val currentInterestRate =
                    instrumentReports.sumOf { it.currentInterestRate }.divide(inputSize, MathContext.DECIMAL64)

                InstrumentInterestRateReport(
                    instrument = instrument,
                    totalInterestRate = lastReport.totalInterestRate,
                    currentInterestRate = currentInterestRate,
                    assets = lastReport.assets,
                    positions = lastReport.positions,
                    valuation = lastReport.valuation,
                    valuationDate = lastReport.valuationDate,
                )
            }
        }
    }

    private suspend fun getPreviousInterestRateReport(input: ReportDataResolverInput): ByInstrument<InstrumentInterestRateReport> {
        val openPositions = input.reportTransactionStore
            .getPreviousTransactions()
            .filterOpenPositionsByInstrument()

        val valuationDate = input.interval.getPreviousLastDay()

        return openPositions.mapValues { (instrument, instrumentPositions) ->
            val assets = instrumentPositions.sumOf { it.instrumentRecord.amount }
            val valuation = calculateInstrumentValue(input, valuationDate, instrument, assets)
            val positions = instrumentPositions.toInterestPositions(input)

            InstrumentInterestRateReport(
                instrument = instrument,
                totalInterestRate = BigDecimal.ZERO,
                currentInterestRate = BigDecimal.ZERO,
                assets = assets,
                positions = positions,
                valuation = valuation,
                valuationDate = valuationDate,
            )
        }
    }

    private suspend fun getNextInterestRateReport(
        input: ReportDataResolverInput,
        timeBucket: TimeBucket,
        previous: ByInstrument<InstrumentInterestRateReport>,
    ): ByInstrument<InstrumentInterestRateReport> {
        val bucketOpenPositions = input.reportTransactionStore
            .getBucketTransactions(timeBucket)
            .filterOpenPositionsByInstrument()

        val allInstruments = previous.keys + bucketOpenPositions.keys
        val valuationDate = timeBucket.to

        return allInstruments.associateWith { instrument ->
            val previousReport = previous[instrument]
                ?: InstrumentInterestRateReport.zero(instrument, timeBucket.from.minus(DatePeriod(days = 1)))
            val instrumentPositions = bucketOpenPositions[instrument] ?: emptyList()

            val assets = previousReport.assets + instrumentPositions.sumOf { it.instrumentRecord.amount }
            val valuation = calculateInstrumentValue(input, valuationDate, instrument, assets)

            val currentPositions = instrumentPositions.toInterestPositions(input)
            val allPositions = previousReport.positions + currentPositions
            val previousAggregatedPosition =
                InterestRateCalculationCommand.Position(previousReport.valuationDate, previousReport.valuation)

            val totalInterestRate = calculateInterestRate(allPositions, valuation, valuationDate)
            val currentInterestRate =
                calculateInterestRate(currentPositions + previousAggregatedPosition, valuation, valuationDate)

            InstrumentInterestRateReport(
                instrument = instrument,
                totalInterestRate = totalInterestRate,
                currentInterestRate = currentInterestRate,
                assets = assets,
                positions = allPositions,
                valuation = valuation,
                valuationDate = valuationDate,
            )
        }
    }

    private fun List<ReportTransaction>.filterOpenPositionsByInstrument(): ByInstrument<List<ReportTransaction.OpenPosition>> =
        this.filterIsInstance<ReportTransaction.OpenPosition>()
            .groupBy { it.instrumentRecord.unit as Instrument }

    private suspend fun calculateInstrumentValue(
        input: ReportDataResolverInput,
        date: LocalDate,
        instrument: Instrument,
        amount: BigDecimal,
    ): BigDecimal {
        val userId = input.userId
        val targetCurrency = input.dataConfiguration.currency
        return amount * conversionRateService.getRate(userId, date, instrument, targetCurrency)
    }

    private suspend fun List<ReportTransaction.OpenPosition>.toInterestPositions(
        input: ReportDataResolverInput,
    ): List<InterestRateCalculationCommand.Position> {
        val userId = input.userId
        val valuationCurrency = input.dataConfiguration.currency
        return map { position ->
            val amount = position.currencyRecord.amount.negate() * conversionRateService.getRate(
                userId,
                position.date,
                position.currencyRecord.unit as Currency,
                valuationCurrency
            )
            InterestRateCalculationCommand.Position(position.date, amount)
        }
    }

    private fun calculateInterestRate(
        positions: List<InterestRateCalculationCommand.Position>,
        valuation: BigDecimal,
        valuationDate: LocalDate,
    ): BigDecimal {
        if (positions.none { it.date < valuationDate } || valuation <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        return interestRateCalculator.calculateInterestRate(
            InterestRateCalculationCommand(
                positions = positions,
                valuation = valuation,
                valuationDate = valuationDate
            )
        )
    }
}
