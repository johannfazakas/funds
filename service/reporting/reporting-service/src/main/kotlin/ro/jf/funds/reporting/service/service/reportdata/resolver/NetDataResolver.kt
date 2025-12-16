package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.platform.jvm.observability.tracing.withSpan
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.forecast.ForecastStrategy
import java.math.BigDecimal

class NetDataResolver(
    private val conversionRateService: ConversionRateService,
    private val forecastStrategy: ForecastStrategy,
) : ReportDataResolver<NetReport> {
    override suspend fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<NetReport> = withSuspendingSpan {
        input.interval
            .generateBucketedData { timeBucket: TimeBucket ->
                getNet(input, input.reportTransactionStore.getBucketRecordsByUnit(timeBucket))
            }
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<NetReport>,
    ): ByBucket<NetReport> = withSpan("forecast") {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<NetReport>, _ ->
            NetReport(forecastStrategy.forecastNext(inputBuckets.map { it.net }))
        }
    }

    private suspend fun getNet(
        input: ReportDataResolverInput,
        records: ByUnit<List<ReportRecord>>,
    ): NetReport = NetReport(sumValue(input, filterRecords(input, records)))

    private fun filterRecords(input: ReportDataResolverInput, records: ByUnit<List<ReportRecord>>): List<ReportRecord> {
        val filter = input.dataConfiguration.reports.net.filter
            ?.let { filter -> { record: ReportRecord -> filter.test(record) } }
            ?: { true }
        return records
            .flatMap { it.value }
            .filter(filter)
    }

    private suspend fun sumValue(
        input: ReportDataResolverInput,
        records: List<ReportRecord>,
    ): BigDecimal {
        return records
            .sumOf { record: ReportRecord ->
                val rate =
                    conversionRateService.getRate(
                        record.date,
                        record.unit,
                        input.dataConfiguration.currency
                    )
                record.amount * rate
            }
    }
}
