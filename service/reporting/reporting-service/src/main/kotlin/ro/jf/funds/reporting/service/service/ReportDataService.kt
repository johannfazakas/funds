package ro.jf.funds.reporting.service.service

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
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
        val catalog = RecordCatalog(reportRecords, granularInterval)
        val conversions = getConversions(userId, reportView.dataConfiguration.currency, reportRecords, granularInterval)

        val dataBuckets = granularInterval
            .generateBucketedData(
                seedFunction = { bucket -> getSeedData(bucket, catalog, reportView, conversions) },
                nextFunction = { bucket, previous -> getNextData(bucket, catalog, reportView, previous, conversions) }
            )
            .toList()

        return ReportData(reportViewId, granularInterval, dataBuckets)
    }

    private fun getSeedData(
        interval: DateInterval, catalog: RecordCatalog, reportView: ReportView, conversions: ConversionsResponse,
    ): ReportDataAggregate {
        return ReportDataAggregate(
            net = getNet(
                catalog.getRecordsByBucket(interval),
                reportView.dataConfiguration
            ),
            value = getValueReport(
                interval,
                getAmountByUnit(catalog.previousRecords),
                catalog.getRecordsByBucket(interval),
                conversions,
                reportView.dataConfiguration
            )
        )
    }

    private fun getNextData(
        interval: DateInterval,
        catalog: RecordCatalog,
        reportView: ReportView,
        previous: ReportDataAggregate,
        conversions: ConversionsResponse,
    ): ReportDataAggregate {
        return ReportDataAggregate(
            net = getNet(
                catalog.getRecordsByBucket(interval),
                reportView.dataConfiguration
            ),
            value = getValueReport(
                interval,
                previous.value?.endAmountByUnit ?: ByUnit(emptyMap()),
                catalog.getRecordsByBucket(interval),
                conversions,
                reportView.dataConfiguration
            )
        )
    }

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

        val dates = granularInterval.getBuckets().flatMap { listOf(it.from, it.to) }.toList()

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

    private fun getNet(
        records: ByUnit<List<ReportRecord>>,
        reportDataConfiguration: ReportDataConfiguration,
    ): BigDecimal? {
        if (!reportDataConfiguration.features.net.enabled) {
            return null
        }
        val recordFilter: (ReportRecord) -> Boolean = if (reportDataConfiguration.features.net.applyFilter)
            { record -> record.labels.any { label -> label in (reportDataConfiguration.filter.labels ?: emptyList()) } }
        else
            { _ -> true }
        return records
            .flatMap { it.value }
            .filter(recordFilter)
            // TODO(Johann) is this correct?
            .sumOf { it.reportCurrencyAmount }
    }

    private fun getValueReport(
        bucket: DateInterval,
        startAmountByUnit: ByUnit<BigDecimal>,
        bucketRecords: ByUnit<List<ReportRecord>>,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): ValueReport? {
        if (!reportDataConfiguration.features.valueReport.enabled) {
            return null
        }
        val amountByUnit = getAmountByUnit(bucketRecords)
        val endAmountByUnit = amountByUnit + startAmountByUnit

        val startValue = startAmountByUnit.valueAt(bucket.from, reportDataConfiguration.currency, conversions)
        val endValue = endAmountByUnit.valueAt(bucket.to, reportDataConfiguration.currency, conversions)

        return ValueReport(startValue, endValue, BigDecimal.ZERO, BigDecimal.ZERO, endAmountByUnit)
    }

    private fun getAmountByUnit(records: ByUnit<List<ReportRecord>>): ByUnit<BigDecimal> =
        records.mapValues { _, items -> items.sumOf { it.amount } }

    private operator fun ByUnit<BigDecimal>.plus(other: ByUnit<BigDecimal>): ByUnit<BigDecimal> {
        return listOf(this, other)
            .asSequence()
            .flatMap { it.asSequence().map { (unit, value) -> unit to value } }
            .groupBy { it.first }
            .mapValues { (_, values) -> values.sumOf { it.second } }
            .let(::ByUnit)
    }

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
}
