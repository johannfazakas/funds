package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculationCommand
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculator
import java.math.BigDecimal

class InterestRateReportResolver(
    private val conversionRateService: ConversionRateService,
    private val interestRateCalculator: InterestRateCalculator,
) : ReportDataResolver<InterestRateReport> {

    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<InterestRateReport> = withSuspendingSpan {
        input.interval
            .generateBucketedData(getPreviousInterestRateReport(input)) { timeBucket, previous ->
                getNextInterestRateReport(input, timeBucket, previous)
            }
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<InterestRateReport>,
    ): ByBucket<InterestRateReport> = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<InterestRateReport> ->
            val lastBucket = inputBuckets.last()
            InterestRateReport(
                totalInterestRate = BigDecimal.ZERO,
                currentInterestRate = BigDecimal.ZERO,
                assetsByInstrument = lastBucket.assetsByInstrument,
                positions = lastBucket.positions,
                valuation = lastBucket.valuation,
                valuationDate = lastBucket.valuationDate,
            )
        }
    }

    private suspend fun getPreviousInterestRateReport(input: ReportDataResolverInput): InterestRateReport {
        val openPositions = input.reportTransactionStore
            .getPreviousTransactions()
            .filterOpenPositions()

        val valuationDate = input.interval.getPreviousLastDay()
        val assetsByInstrument = extractAssetsByInstrument(openPositions)
        val valuation = calculateAssetsValue(input, valuationDate, assetsByInstrument)
        val positions = openPositions.toInterestPositions(input)

        return InterestRateReport(
            totalInterestRate = BigDecimal.ZERO,
            currentInterestRate = BigDecimal.ZERO,
            assetsByInstrument = assetsByInstrument,
            positions = positions,
            valuation = valuation,
            valuationDate = valuationDate,
        )
    }

    private suspend fun getNextInterestRateReport(
        input: ReportDataResolverInput,
        timeBucket: TimeBucket,
        previous: InterestRateReport,
    ): InterestRateReport {
        val bucketOpenPositions = input.reportTransactionStore
            .getBucketTransactions(timeBucket)
            .filterOpenPositions()

        val assetsByInstrument = mergeMaps(previous.assetsByInstrument, extractAssetsByInstrument(bucketOpenPositions))
        val valuation = calculateAssetsValue(input, timeBucket.to, assetsByInstrument)

        val currentPositions = bucketOpenPositions.toInterestPositions(input)
        val allPositions = previous.positions + currentPositions
        val previousAggregatedPosition =
            InterestRateCalculationCommand.Position(previous.valuationDate, previous.valuation)

        val totalInterestRate = calculateInterestRate(allPositions, valuation, timeBucket.to)
        val currentInterestRate =
            calculateInterestRate(currentPositions + previousAggregatedPosition, valuation, timeBucket.to)

        return InterestRateReport(
            totalInterestRate = totalInterestRate,
            currentInterestRate = currentInterestRate,
            assetsByInstrument = assetsByInstrument,
            positions = allPositions,
            valuation = valuation,
            valuationDate = timeBucket.to,
        )
    }

    private fun List<ReportTransaction>.filterOpenPositions(): List<ReportTransaction.OpenPosition> =
        this.filterIsInstance<ReportTransaction.OpenPosition>()

    private fun extractAssetsByInstrument(
        openPositions: List<ReportTransaction.OpenPosition>,
    ): Map<Instrument, BigDecimal> =
        openPositions
            .groupBy { it.instrumentRecord.unit as Instrument }
            .mapValues { (_, positions) ->
                positions.sumOf { it.instrumentRecord.amount }
            }

    private suspend fun calculateAssetsValue(
        input: ReportDataResolverInput,
        date: LocalDate,
        assetsByInstrument: Map<Instrument, BigDecimal>,
    ): BigDecimal {
        val userId = input.userId
        val targetCurrency = input.dataConfiguration.currency
        return assetsByInstrument
            .map { (instrument, amount) ->
                amount * conversionRateService.getRate(userId, date, instrument, targetCurrency)
            }
            .sumOf { it }
    }

    private suspend fun List<ReportTransaction.OpenPosition>.toInterestPositions(
        input: ReportDataResolverInput,
    ): List<InterestRateCalculationCommand.Position> {
        val userId = input.userId
        val valuationCurrency = input.dataConfiguration.currency
        return map { position ->
            val rate = conversionRateService.getRate(
                userId, position.date, position.currencyRecord.unit as Currency, valuationCurrency
            )
            val amount = position.currencyRecord.amount.negate() * rate
            InterestRateCalculationCommand.Position(position.date, amount)
        }
    }

    private fun calculateInterestRate(
        positions: List<InterestRateCalculationCommand.Position>,
        valuation: BigDecimal,
        valuationDate: LocalDate,
    ): BigDecimal {
        if (positions.isEmpty() || valuation <= BigDecimal.ZERO) {
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

    private fun mergeMaps(
        one: Map<Instrument, BigDecimal>,
        two: Map<Instrument, BigDecimal>,
    ): Map<Instrument, BigDecimal> {
        return sequenceOf(one, two)
            .flatMap { it.entries.map { entry -> entry.key to entry.value } }
            .groupBy { (key, _) -> key }
            .mapValues { (_, values) -> values.sumOf { it.second } }
    }
}
