package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import ro.jf.funds.reporting.service.service.generateForecastData
import ro.jf.funds.reporting.service.utils.withSpan
import java.math.BigDecimal
import java.math.MathContext

class ValueReportDataResolver : ReportDataResolver<ValueReport> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ValueReport>? = withSpan("resolve") {
        if (!input.dataConfiguration.features.valueReport.enabled) {
            return@withSpan null
        }
        input.dateInterval
            .generateBucketedData(
                { interval ->
                    getValueReport(
                        interval,
                        getAmountByUnit(input.catalog.getPreviousRecordsGroupedByUnit()),
                        input.catalog.getBucketRecordsGroupedByUnit(interval),
                        input.conversions,
                        input.dataConfiguration
                    )
                },
                { interval, previous ->
                    getValueReport(
                        interval,
                        previous.endAmountByUnit,
                        input.catalog.getBucketRecordsGroupedByUnit(interval),
                        input.conversions,
                        input.dataConfiguration
                    )
                }
            )
            .let(::ByBucket)
    }

    override fun forecast(
        input: ReportDataForecastInput<ValueReport>,
    ): ByBucket<ValueReport> = withSpan("forecast") {
        val inputSize = input.forecastConfiguration.inputBuckets.toBigDecimal()
        input.dateInterval.generateForecastData(
            input.forecastConfiguration.outputBuckets,
            input.forecastConfiguration.inputBuckets,
            { interval -> input.realData[interval] }
        ) { inputBuckets: List<ValueReport> ->
            val first = inputBuckets.first()
            val last = inputBuckets.last()

            ValueReport(
                start = last.end,
                end = last.end + ((last.end - first.end).divide(inputSize, MathContext.DECIMAL64)),
                min = last.min + ((last.min - first.min).divide(inputSize, MathContext.DECIMAL64)),
                max = last.max + ((last.max - first.max).divide(inputSize, MathContext.DECIMAL64)),
                endAmountByUnit = ByUnit(emptyMap())
            )
        }.let { ByBucket(it) }
    }

    private fun getValueReport(
        bucket: DateInterval,
        startAmountByUnit: ByUnit<BigDecimal>,
        bucketRecords: ByUnit<List<ReportRecord>>,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): ValueReport {
        val amountByUnit = getAmountByUnit(bucketRecords)
        val endAmountByUnit = amountByUnit.add(startAmountByUnit)

        val startValue = startAmountByUnit.valueAt(bucket.from, reportDataConfiguration.currency, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, reportDataConfiguration.currency, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(records: ByUnit<List<ReportRecord>>): ByUnit<BigDecimal> =
        records.mapValues { (_, items) -> items.sumOf { it.amount } }

    private fun ByUnit<BigDecimal>.valueAt(
        date: LocalDate,
        currency: Currency,
        conversions: ConversionsResponse,
    ): BigDecimal {
        return this
            .map { (unit, amount) ->
                val rate = if (unit == currency) BigDecimal.ONE else conversions.getRate(unit, currency, date)
                // TODO(Johann) this error should be handled in API
                    ?: error("No conversion rate found for $unit to $currency at $date")
                amount * rate
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