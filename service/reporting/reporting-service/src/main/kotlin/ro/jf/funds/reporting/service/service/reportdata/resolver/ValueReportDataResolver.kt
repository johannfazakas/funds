package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.forecast.ForecastStrategy
import java.math.BigDecimal

class ValueReportDataResolver(
    private val conversionRateService: ConversionRateService,
    private val forecastStrategy: ForecastStrategy,
) : ReportDataResolver<ValueReport> {
    override suspend fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ValueReport> = withSuspendingSpan {
        val filter = getFilter(input.dataConfiguration.reports.valueReport)
        input.interval
            .generateBucketedData(
                ValueReport(
                    endAmountByUnit = getAmountByUnit(input.reportTransactionStore.getPreviousRecordsByUnit(), filter)
                )
            ) { timeBucket, previous ->
                getValueReport(
                    timeBucket,
                    previous.endAmountByUnit,
                    input.reportTransactionStore.getBucketRecordsByUnit(timeBucket),
                    input.dataConfiguration,
                    filter
                )
            }
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<ValueReport>,
    ): ByBucket<ValueReport> = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ValueReport>, _ ->
            ValueReport(
                start = inputBuckets.last().end,
                end = forecastStrategy.forecastNext(inputBuckets.map { it.end }),
                min = forecastStrategy.forecastNext(inputBuckets.map { it.min }),
                max = forecastStrategy.forecastNext(inputBuckets.map { it.max }),
                endAmountByUnit = emptyMap()
            )
        }
    }

    private fun getFilter(
        configuration: ValueReportConfiguration,
    ): (ReportRecord) -> Boolean =
        configuration.filter
            ?.let { f -> { record: ReportRecord -> f.test(record) } }
            ?: { _: ReportRecord -> true }

    private suspend fun getValueReport(
        bucket: TimeBucket,
        startAmountByUnit: ByUnit<BigDecimal>,
        bucketRecords: ByUnit<List<ReportRecord>>,
        reportDataConfiguration: ReportDataConfiguration,
        filter: (ReportRecord) -> Boolean,
    ): ValueReport {
        val amountByUnit = getAmountByUnit(bucketRecords, filter)
        val endAmountByUnit = amountByUnit.add(startAmountByUnit)

        val startValue = startAmountByUnit.valueAt(bucket.from, reportDataConfiguration.currency)
        val endValue = endAmountByUnit.valueAt(bucket.to, reportDataConfiguration.currency)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(
        records: ByUnit<List<ReportRecord>>,
        filter: (ReportRecord) -> Boolean,
    ): ByUnit<BigDecimal> =
        records.mapValues { (_, items) -> items.filter(filter).sumOf { it.amount } }

    private suspend fun ByUnit<BigDecimal>.valueAt(
        date: LocalDate,
        currency: Currency,
    ): BigDecimal {
        return this
            .map { (unit, amount) ->
                amount * conversionRateService.getRate(date, unit, currency)
            }
            .sumOf { it }
    }

    private fun ByUnit<BigDecimal>.add(other: ByUnit<BigDecimal>): ByUnit<BigDecimal> {
        return listOf(this, other)
            .asSequence()
            .flatMap { it.asSequence().map { (unit, value) -> unit to value } }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.sumOf { it.second } }
    }
}