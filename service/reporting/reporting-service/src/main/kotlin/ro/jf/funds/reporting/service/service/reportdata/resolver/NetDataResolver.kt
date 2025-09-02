package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext

class NetDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<NetReport> {
    override suspend fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<NetReport>? = withSuspendingSpan {
        if (!input.dataConfiguration.reports.net.enabled) {
            return@withSuspendingSpan null
        }
        input.interval
            .generateBucketedData { timeBucket: TimeBucket ->
                getNet(input, input.recordStore.getBucketRecordsByUnit(timeBucket))
            }
            .let { ByBucket(it) }
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<NetReport>,
    ): ByBucket<NetReport> = withSpan("forecast") {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<NetReport> ->
            val inputSize = inputBuckets.size.toBigDecimal()
            NetReport(inputBuckets.sumOf { it.net }.divide(inputSize, MathContext.DECIMAL64))
        }.let { ByBucket(it) }
    }

    private suspend fun getNet(
        input: ReportDataResolverInput,
        records: ByUnit<List<ReportRecord>>,
    ): NetReport {
        val filter = input.dataConfiguration.reports.net.filter
            ?.let { filter -> { record: ReportRecord -> filter.test(record) } }
            ?: { true }
        return NetReport(getFilteredNet(input, records, filter))
    }

    private suspend fun getFilteredNet(
        input: ReportDataResolverInput,
        records: ByUnit<List<ReportRecord>>,
        recordFilter: (ReportRecord) -> Boolean,
    ): BigDecimal {
        return records
            .flatMap { it.value }
            .filter(recordFilter::invoke)
            .sumOf { record: ReportRecord ->
                val rate =
                    conversionRateService.getRate(
                        input.userId,
                        record.date,
                        record.unit,
                        input.dataConfiguration.currency
                    )
                record.amount * rate
            }
    }
}
