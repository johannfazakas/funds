package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class GroupedNetDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<ByGroup<NetReport>> {
    override suspend fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ByGroup<NetReport>> = withSuspendingSpan {
        input.interval
            .generateBucketedData { timeBucket ->
                getGroupedNet(
                    input.userId,
                    input.reportTransactionStore.getBucketRecordsByUnit(timeBucket),
                    input.dataConfiguration.groups ?: emptyList(),
                    input.dataConfiguration.currency
                )
            }
            .let(::ByBucket)
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<ByGroup<NetReport>>,
    ): ByBucket<ByGroup<NetReport>> = withSpan("forecast") {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ByGroup<NetReport>> ->
            val inputBucketsSize = inputBuckets.size.toBigDecimal()
            input.groups
                .associateWith { group ->
                    input.forecastConfiguration.inputBuckets.toBigDecimal()
                    inputBuckets.sumOf { it[group]?.net ?: BigDecimal.ZERO }
                        .divide(inputBucketsSize, MathContext.DECIMAL64)
                        .let(::NetReport)
                }
                .let { ByGroup(it) }
        }.let { ByBucket(it) }
    }

    private suspend fun getGroupedNet(
        userId: UUID,
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
        reportCurrency: Currency,
    ): ByGroup<NetReport> {
        return groups.associate { group ->
            group.name to getFilteredNet(userId, records, reportCurrency, group.filter::test)
        }.let(::ByGroup)
    }

    private suspend fun getFilteredNet(
        userId: UUID,
        records: ByUnit<List<ReportRecord>>,
        reportCurrency: Currency,
        recordFilter: (ReportRecord) -> Boolean,
    ): NetReport {
        return records
            .flatMap { it.value }
            .filter(recordFilter)
            .sumOf {
                it.amount * conversionRateService.getRate(userId, it.date, it.unit, reportCurrency)
            }
            .let(::NetReport)
    }
}
