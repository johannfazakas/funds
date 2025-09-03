package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class ValueReportDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<ValueReport> {
    override suspend fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ValueReport> = withSuspendingSpan {
        val filter = getFilter(input.dataConfiguration.reports.valueReport)
        input.interval
            .generateBucketedData(
                ValueReport(
                    endAmountByUnit = getAmountByUnit(input.recordStore.getPreviousRecordsByUnit(), filter)
                )
            ) { timeBucket, previous ->
                getValueReport(
                    input.userId,
                    timeBucket,
                    previous.endAmountByUnit,
                    input.recordStore.getBucketRecordsByUnit(timeBucket),
                    input.dataConfiguration,
                    filter
                )
            }
            .let(::ByBucket)
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<ValueReport>,
    ): ByBucket<ValueReport> = withSuspendingSpan {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ValueReport> ->
            val first = inputBuckets.first()
            val last = inputBuckets.last()
            val inputSize = inputBuckets.size.toBigDecimal()

            ValueReport(
                start = last.end,
                end = last.end + ((last.end - first.end).divide(inputSize, MathContext.DECIMAL64)),
                min = last.min + ((last.min - first.min).divide(inputSize, MathContext.DECIMAL64)),
                max = last.max + ((last.max - first.max).divide(inputSize, MathContext.DECIMAL64)),
                endAmountByUnit = ByUnit(emptyMap())
            )
        }.let { ByBucket(it) }
    }

    private fun getFilter(
        configuration: ValueReportConfiguration,
    ): (ReportRecord) -> Boolean =
        configuration.filter
            ?.let { f -> { record: ReportRecord -> f.test(record) } }
            ?: { _: ReportRecord -> true }

    private suspend fun getValueReport(
        userId: UUID,
        bucket: TimeBucket,
        startAmountByUnit: ByUnit<BigDecimal>,
        bucketRecords: ByUnit<List<ReportRecord>>,
        reportDataConfiguration: ReportDataConfiguration,
        filter: (ReportRecord) -> Boolean,
    ): ValueReport {
        val amountByUnit = getAmountByUnit(bucketRecords, filter)
        val endAmountByUnit = amountByUnit.add(startAmountByUnit)

        val startValue = startAmountByUnit.valueAt(userId, bucket.from, reportDataConfiguration.currency)
        val endValue = endAmountByUnit.valueAt(userId, bucket.to, reportDataConfiguration.currency)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(
        records: ByUnit<List<ReportRecord>>,
        filter: (ReportRecord) -> Boolean,
    ): ByUnit<BigDecimal> =
        records.mapValues { (_, items) -> items.filter(filter).sumOf { it.amount } }

    private suspend fun ByUnit<BigDecimal>.valueAt(
        userId: UUID,
        date: LocalDate,
        currency: Currency,
    ): BigDecimal {
        return this
            .map { (unit, amount) ->
                amount * conversionRateService.getRate(userId, date, unit, currency)
            }
            .sumOf { it }
    }

    private fun ByUnit<BigDecimal>.add(other: ByUnit<BigDecimal>): ByUnit<BigDecimal> {
        return listOf(this, other)
            .asSequence()
            .flatMap { it.asSequence().map { (unit, value) -> unit to value } }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.sumOf { it.second } }
            .let(::ByUnit)
    }
}