package ro.jf.funds.reporting.service.service

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.*


private val log = logger { }

class ReportDataService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData {
        // TODO(Johann) dive into logging a bit. how can it be controlled in a ktor service? This should probably be a DEBUG
        log.info { "Get report view data for user $userId, report $reportViewId and interval $granularInterval" }
        val reportView = reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
        val reportRecords = reportRecordRepository
            .findByViewUntil(userId, reportViewId, granularInterval.interval.to)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval ${granularInterval.interval}" }
        // TODO(Johann) might be nicer if catalog would also work on DateInterval buckets
        val catalog = RecordCatalog(reportRecords, granularInterval)
        val conversions = getConversions(userId, reportView.currency, reportRecords, granularInterval)

        val dataBuckets = granularInterval.generateBucketedData(
            seedFunction = { bucket -> getSeedData(bucket, catalog, reportView, conversions) },
            nextFunction = { bucket, previous -> getNextData(bucket, catalog, reportView, previous, conversions) }
        )

        return ReportData(reportViewId, granularInterval, dataBuckets)
    }

    private fun getSeedData(
        interval: DateInterval, catalog: RecordCatalog, reportView: ReportView, conversions: ConversionsResponse,
    ) = ReportDataBucket(
        timeBucket = interval.from,
        amount = getNet(catalog.getRecordsByBucket(interval), reportView.labels),
        value = getSeedValueReport(
            interval,
            reportView.currency,
            catalog.previousRecords,
            catalog.getRecordsByBucket(interval),
            conversions
        )
    )

    private fun getNextData(
        interval: DateInterval,
        catalog: RecordCatalog,
        reportView: ReportView,
        previous: ReportDataBucket,
        conversions: ConversionsResponse,
    ) = ReportDataBucket(
        timeBucket = interval.from,
        amount = getNet(catalog.getRecordsByBucket(interval), reportView.labels),
        value = getNextValueReport(
            interval,
            reportView.currency,
            previous.value,
            catalog.getRecordsByBucket(interval),
            conversions
        )
    )

    private suspend fun getConversions(
        userId: UUID,
        targetUnit: Currency,
        reportRecords: List<ReportRecord>,
        granularInterval: GranularDateInterval,
    ): ConversionsResponse {
        val sourceUnits = reportRecords
            .asSequence()
            .map { it.unit }
            .filter { it != targetUnit }
            .distinct().toList()

        val dates = granularInterval.getBuckets().flatMap { listOf(it.from, it.to) }

        val conversionsRequest = ConversionsRequest(
            conversions = sourceUnits
                .asSequence()
                .flatMap { sourceUnit -> dates.map { sourceUnit to it } }
                .map { (sourceUnit, date) ->
                    ConversionRequest(sourceUnit, targetUnit, date)
                }
                .toList()
        )
        return historicalPricingSdk.convert(userId, conversionsRequest)
    }

    private fun getNet(records: Map<FinancialUnit, List<ReportRecord>>, labels: List<Label>): BigDecimal {
        return records
            .flatMap { it.value }
            .filter { it.labels.any { label -> label in labels } }
            // TODO(Johann) is this correct?
            .sumOf { it.reportCurrencyAmount }
    }

    // TODO(Johann) extract duplicate
    private fun getSeedValueReport(
        bucket: DateInterval,
        targetUnit: Currency,
        previousRecords: Map<FinancialUnit, List<ReportRecord>>,
        bucketRecords: Map<FinancialUnit, List<ReportRecord>>,
        conversions: ConversionsResponse,
    ): ValueReport {
        val startAmountByUnit = getAmountByUnit(previousRecords)
        val endAmountByUnit = getAmountByUnit(bucketRecords) + startAmountByUnit

        val startValue = startAmountByUnit.valueAt(bucket.from, targetUnit, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, targetUnit, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getNextValueReport(
        bucket: DateInterval,
        targetUnit: Currency,
        previousReport: ValueReport,
        bucketRecords: Map<FinancialUnit, List<ReportRecord>>,
        conversions: ConversionsResponse,
    ): ValueReport {
        val startAmountByUnit = previousReport.endAmountByUnit
        val endAmountByUnit = getAmountByUnit(bucketRecords) + startAmountByUnit

        val startValue = startAmountByUnit.valueAt(bucket.from, targetUnit, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, targetUnit, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getValueReport(
        bucket: DateInterval,
        targetUnit: Currency,
        previousReport: ValueReport,
        bucketRecords: Map<FinancialUnit, List<ReportRecord>>,
        conversions: ConversionsResponse,
    ): ValueReport {
        val startAmountByUnit = previousReport.endAmountByUnit
        val endAmountByUnit = getAmountByUnit(bucketRecords) + startAmountByUnit

        val startValue = startAmountByUnit.valueAt(bucket.from, targetUnit, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, targetUnit, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(records: Map<FinancialUnit, List<ReportRecord>>): Map<FinancialUnit, BigDecimal> {
        return records.mapValues { (_, records) -> records.sumOf { it.amount } }
    }

    private operator fun Map<FinancialUnit, BigDecimal>.plus(other: Map<FinancialUnit, BigDecimal>): Map<FinancialUnit, BigDecimal> {
        return listOf(this, other)
            .asSequence()
            .flatMap { it.entries.map { entry -> entry.key to entry.value } }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.sumOf { it.second } }
    }

    private fun Map<FinancialUnit, BigDecimal>.valueAt(
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
}
