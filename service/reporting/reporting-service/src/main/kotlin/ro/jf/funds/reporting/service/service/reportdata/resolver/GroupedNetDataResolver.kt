package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.forecast.ForecastStrategy
import java.math.BigDecimal
import java.util.*

class GroupedNetDataResolver(
    private val conversionRateService: ConversionRateService,
    private val forecastStrategy: ForecastStrategy,
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
    }

    override suspend fun forecast(
        input: ReportDataForecastInput<ByGroup<NetReport>>,
    ): ByBucket<ByGroup<NetReport>> = withSpan("forecast") {
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ByGroup<NetReport>> ->
            input.groups
                .associateWith { group ->
                    val netValues = inputBuckets.map { it[group]?.net ?: BigDecimal.ZERO }
                    forecastStrategy.forecastNext(netValues).let(::NetReport)
                }
        }
    }

    private suspend fun getGroupedNet(
        userId: UUID,
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
        reportCurrency: Currency,
    ): ByGroup<NetReport> =
        groups.associate { group ->
            group.name to sumNet(userId, reportCurrency, filterGroupRecords(group, records))
        }

    private fun filterGroupRecords(
        group: ReportGroup, records: ByUnit<List<ReportRecord>>,
    ): List<ReportRecord> {
        return records
            .flatMap { it.value }
            .filter(group.filter::test)
    }

    private suspend fun sumNet(
        userId: UUID,
        reportCurrency: Currency,
        records: List<ReportRecord>,
    ): NetReport {
        return records
            .sumOf {
                it.amount * conversionRateService.getRate(userId, it.date, it.unit, reportCurrency)
            }
            .let(::NetReport)
    }
}
