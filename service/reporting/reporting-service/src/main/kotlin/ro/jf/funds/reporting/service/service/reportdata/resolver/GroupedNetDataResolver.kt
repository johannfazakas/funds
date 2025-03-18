package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.service.domain.ByUnit
import ro.jf.funds.reporting.service.domain.ReportGroup
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.service.generateBucketedData
import java.math.BigDecimal

class GroupedNetDataResolver : ReportDataResolver<Map<String, BigDecimal>> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): Map<DateInterval, Map<String, BigDecimal>>? {
        if (!input.dataConfiguration.features.groupedNet.enabled || input.dataConfiguration.groups == null) {
            return null
        }
        return input.dateInterval
            .generateBucketedData(
                { interval ->
                    getGroupedNet(
                        input.catalog.getRecordsByBucket(interval),
                        input.dataConfiguration.groups
                    )
                },
                { interval, _ ->
                    getGroupedNet(
                        input.catalog.getRecordsByBucket(interval),
                        input.dataConfiguration.groups
                    )
                }
            )
    }

    private fun getGroupedNet(
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
    ): Map<String, BigDecimal> {
        return groups.associate { group ->
            group.name to getFilteredNet(records, group.filter::test)
        }
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