package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import ro.jf.funds.reporting.service.service.generateForecastData
import java.math.BigDecimal

class GroupedNetDataResolver : ReportDataResolver<ByGroup<BigDecimal>> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ByGroup<BigDecimal>>? {
        if (!input.dataConfiguration.features.groupedNet.enabled || input.dataConfiguration.groups == null) {
            return null
        }
        return input.dateInterval
            .generateBucketedData(
                { interval ->
                    getGroupedNet(
                        input.catalog.getBucketRecordsGroupedByUnit(interval),
                        input.dataConfiguration.groups
                    )
                },
                { interval, _ ->
                    getGroupedNet(
                        input.catalog.getBucketRecordsGroupedByUnit(interval),
                        input.dataConfiguration.groups
                    )
                }
            )
            .let(::ByBucket)
    }

    override fun forecast(input: ReportDataForecastInput<ByGroup<BigDecimal>>): ByBucket<ByGroup<BigDecimal>> {
        return input.dateInterval.generateForecastData(
            input.forecastConfiguration.forecastBuckets,
            input.forecastConfiguration.forecastInputBuckets,
            { interval -> input.realData[interval] }
        ) { inputBuckets: List<ByGroup<BigDecimal>> ->
            input.groups
                .associateWith { group ->
                    inputBuckets.sumOf { it[group] ?: BigDecimal.ZERO }.divide(inputBuckets.size.toBigDecimal())
                }.let { ByGroup(it) }
        }.let { ByBucket(it) }
    }

    private fun getGroupedNet(
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
    ): ByGroup<BigDecimal> {
        return groups.associate { group ->
            group.name to getFilteredNet(records, group.filter::test)
        }.let(::ByGroup)
    }

    private fun getFilteredNet(
        records: ByUnit<List<ReportRecord>>,
        recordFilter: (ReportRecord) -> Boolean,
    ): BigDecimal {
        return records
            .flatMap { it.value }
            .filter(recordFilter)
            // TODO(Johann) is this correct?
            .sumOf { it.reportCurrencyAmount }
    }
}