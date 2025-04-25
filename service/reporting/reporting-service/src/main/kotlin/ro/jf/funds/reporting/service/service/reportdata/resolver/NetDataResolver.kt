package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ByUnit
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.service.generateBucketedData
import java.math.BigDecimal

// TODO(Johann) shouldn't this be called spent/earned
class NetDataResolver : ReportDataResolver<BigDecimal> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<BigDecimal>? {
        if (!input.dataConfiguration.features.net.enabled) {
            return null
        }
        return input.dateInterval
            .generateBucketedData(
                { interval -> getNet(input.catalog.getByBucket(interval), input.dataConfiguration) },
                { interval, _ -> getNet(input.catalog.getByBucket(interval), input.dataConfiguration) }
            )
            .let { ByBucket(it) }
    }

    private fun getNet(
        records: ByUnit<List<ReportRecord>>,
        reportDataConfiguration: ReportDataConfiguration,
    ): BigDecimal {
        val recordFilter: (ReportRecord) -> Boolean = if (reportDataConfiguration.features.net.applyFilter)
            { record -> record.labels.any { label -> label in (reportDataConfiguration.filter.labels ?: emptyList()) } }
        else
            { _ -> true }
        return getFilteredNet(records, recordFilter)
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