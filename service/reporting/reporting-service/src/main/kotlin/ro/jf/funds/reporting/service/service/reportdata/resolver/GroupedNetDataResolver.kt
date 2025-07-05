package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.utils.getConversionRate
import java.math.BigDecimal
import java.math.MathContext

class GroupedNetDataResolver : ReportDataResolver<ByGroup<BigDecimal>> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): ByBucket<ByGroup<BigDecimal>>? = withSpan("resolve") {
        if (!input.dataConfiguration.reports.groupedNet.enabled || input.dataConfiguration.groups == null) {
            return@withSpan null
        }
        input.interval
            .generateBucketedData(
                { interval ->
                    getGroupedNet(
                        input.catalog.getBucketRecordsGroupedByUnit(interval),
                        input.dataConfiguration.groups,
                        input.conversions,
                        input.dataConfiguration.currency
                    )
                }
            )
            .let(::ByBucket)
    }

    override fun forecast(
        input: ReportDataForecastInput<ByGroup<BigDecimal>>,
    ): ByBucket<ByGroup<BigDecimal>> = withSpan("forecast") {
        val inputBucketsSize = input.forecastConfiguration.inputBuckets.toBigDecimal()
        input.interval.generateForecastData(
            input.forecastConfiguration.inputBuckets,
            input.realData
        ) { inputBuckets: List<ByGroup<BigDecimal>> ->
            val inputBucketsSize = inputBuckets.size.toBigDecimal()
            input.groups
                .associateWith { group ->
                    input.forecastConfiguration.inputBuckets.toBigDecimal()
                    inputBuckets.sumOf { it[group] ?: BigDecimal.ZERO }
                        .divide(inputBucketsSize, MathContext.DECIMAL64)
                }.let { ByGroup(it) }
        }.let { ByBucket(it) }
    }

    private fun getGroupedNet(
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
        conversions: ConversionsResponse,
        reportCurrency: Currency,
    ): ByGroup<BigDecimal> {
        return groups.associate { group ->
            group.name to getFilteredNet(records, conversions, reportCurrency, group.filter::test)
        }.let(::ByGroup)
    }

    private fun getFilteredNet(
        records: ByUnit<List<ReportRecord>>,
        conversions: ConversionsResponse,
        reportCurrency: Currency,
        recordFilter: (ReportRecord) -> Boolean,
    ): BigDecimal {
        return records
            .flatMap { it.value }
            .filter(recordFilter)
            .sumOf {
                it.amount * getConversionRate(conversions, it.date, it.unit, reportCurrency)
            }
    }
}
