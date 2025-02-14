package ro.jf.funds.reporting.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.generateTimeBucketedData
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

        val dataBuckets = generateTimeBucketedData(
            interval = granularInterval,
            seedFunction = { date ->
                ReportDataBucket(
                    timeBucket = date,
                    amount = getNet(catalog.getRecordsByBucket(date), reportView.labels),
                    value = getSeedValueReport(catalog.previousRecords, catalog.getRecordsByBucket(date))
                )
            },
            nextFunction = { date, previous ->
                ReportDataBucket(
                    timeBucket = date,
                    amount = getNet(catalog.getRecordsByBucket(date), reportView.labels),
                    value = getNextValueReport(previous.value, catalog.getRecordsByBucket(date))
                )
            }
        ).map { it.second }

        return ReportData(reportViewId, granularInterval, dataBuckets)
    }

    private fun getNet(records: Map<FinancialUnit, List<ReportRecord>>, labels: List<Label>): BigDecimal {
        return records
            .flatMap { it.value }
            .filter { it.labels.any { label -> label in labels } }
            // TODO(Johann) is this correct?
            .sumOf { it.reportCurrencyAmount }
    }

    private fun getSeedValueReport(
        previousRecords: Map<FinancialUnit, List<ReportRecord>>,
        bucketRecords: Map<FinancialUnit, List<ReportRecord>>,
    ): ValueReport {
        // TODO(Johann) apply currency conversion
        val start = previousRecords.values.sumOf { it.sumOf { it.reportCurrencyAmount } }
        val end = start + bucketRecords.values.sumOf { it.sumOf { it.reportCurrencyAmount } }

        return ValueReport(start, end, BigDecimal.ZERO, BigDecimal.ZERO)
    }

    private fun getNextValueReport(
        previousReport: ValueReport,
        bucketRecords: Map<FinancialUnit, List<ReportRecord>>,
    ): ValueReport {
        // TODO(Johann) apply currency conversion
        val start = previousReport.end
        val end = start + bucketRecords.values.sumOf { it.sumOf { it.reportCurrencyAmount } }

        return ValueReport(start, end, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}
