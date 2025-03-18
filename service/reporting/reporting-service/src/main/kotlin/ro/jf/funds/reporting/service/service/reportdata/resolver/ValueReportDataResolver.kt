package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import java.math.BigDecimal

class ValueReportDataResolver : ReportDataResolver<ValueReport> {
    override fun resolve(
        input: ReportDataResolverInput,
    ): Map<DateInterval, ValueReport>? {
        if (!input.dataConfiguration.features.valueReport.enabled) {
            return null
        }
        return input.dateInterval
            .generateBucketedData(
                { interval ->
                    getValueReport(
                        interval,
                        getAmountByUnit(input.catalog.previousRecords),
                        input.catalog.getRecordsByBucket(interval),
                        input.conversions,
                        input.dataConfiguration
                    )
                },
                { interval, previous ->
                    getValueReport(
                        interval,
                        previous.endAmountByUnit,
                        input.catalog.getRecordsByBucket(interval),
                        input.conversions,
                        input.dataConfiguration
                    )
                }
            )
    }


    private fun getValueReport(
        bucket: DateInterval,
        startAmountByUnit: ByUnit<BigDecimal>,
        bucketRecords: ByUnit<List<ReportRecord>>,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): ValueReport {
        val amountByUnit = getAmountByUnit(bucketRecords)
        val endAmountByUnit = amountByUnit + startAmountByUnit

        val startValue = startAmountByUnit.valueAt(bucket.from, reportDataConfiguration.currency, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, reportDataConfiguration.currency, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(records: ByUnit<List<ReportRecord>>): ByUnit<BigDecimal> =
        records.mapValues { _, items -> items.sumOf { it.amount } }

    private fun ByUnit<BigDecimal>.valueAt(
        date: LocalDate,
        currency: Currency,
        conversions: ConversionsResponse,
    ): BigDecimal {
        return this
            .map { (unit, amount) ->
                val rate = if (unit == currency) BigDecimal.ONE else conversions.getRate(unit, currency, date)
                    ?: error("No conversion rate found for $unit to $currency at $date")
                amount * rate
            }
            .sumOf { it }
    }

    // TODO(Johann-14) could be extracted to a utility class. should it be an operator? not sure
    private operator fun ByUnit<BigDecimal>.plus(other: ByUnit<BigDecimal>): ByUnit<BigDecimal> {
        return listOf(this, other)
            .asSequence()
            .flatMap { it.asSequence().map { (unit, value) -> unit to value } }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.sumOf { it.second } }
            .let(::ByUnit)
    }
}