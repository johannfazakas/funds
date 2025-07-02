package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ByUnit
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.utils.getConversionRate
import ro.jf.funds.reporting.service.utils.withSpan
import java.math.BigDecimal
import java.math.MathContext

class NetDataResolver : ReportDataResolver<BigDecimal> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<BigDecimal>? = withSpan("resolve") {
        if (!input.dataConfiguration.reports.net.enabled) {
            return@withSpan null
        }
        input.interval
            .generateBucketedData { interval ->
                getNet(input.catalog.getBucketRecordsGroupedByUnit(interval), input)
            }
            .let { ByBucket(it) }
    }

    override fun forecast(
        input: ReportDataForecastInput<BigDecimal>,
    ): ByBucket<BigDecimal> = withSpan("forecast") {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<BigDecimal> ->
            val inputSize = inputBuckets.size.toBigDecimal()
            inputBuckets.sumOf { it }.divide(inputSize, MathContext.DECIMAL64)
        }.let { ByBucket(it) }
    }

    private fun getNet(
        records: ByUnit<List<ReportRecord>>,
        input: ReportDataResolverInput,
    ): BigDecimal {
        val recordFilter: (ReportRecord) -> Boolean = if (input.dataConfiguration.reports.net.applyFilter)
            { record -> record.labels.any { label -> label in (input.dataConfiguration.filter.labels ?: emptyList()) } }
        else
            { _ -> true }
        return getFilteredNet(records, recordFilter, input)
    }

    private fun getFilteredNet(
        records: ByUnit<List<ReportRecord>>,
        recordFilter: (ReportRecord) -> Boolean,
        input: ReportDataResolverInput,
    ): BigDecimal {
        return records
            .flatMap { it.value }
            .filter(recordFilter::invoke)
            .sumOf { record: ReportRecord ->
                val rate =
                    getConversionRate(input.conversions, record.date, record.unit, input.dataConfiguration.currency)
                record.amount * rate
            }
    }
}
