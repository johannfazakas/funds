package ro.jf.funds.reporting.service.service

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.generateTimeBucketedData
import ro.jf.funds.reporting.api.model.getTimeBucket
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.*


private val log = logger { }

class ReportDataService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
) {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData {
        // TODO(Johann) dive into logging a bit. how can it be controlled in a ktor service? This should probably be a DEBUG
        log.info { "Get report view data for user $userId, report $reportViewId and interval $granularInterval" }
        val reportView = reportViewRepository.findById(userId, reportViewId)
        reportView
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)

        val reportRecords = reportRecordRepository
            .findByViewUntil(userId, reportViewId, granularInterval.interval.to)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval ${granularInterval.interval}" }

        val (previousRecords, intervalRecords) =
            splitRecordsInBeforeAndDuringInterval(reportRecords, granularInterval)

        val reportRecordsByBucket = intervalRecords
            .groupBy { getTimeBucket(it.date, granularInterval.granularity) }

        val valuesReports = getValueReports(
            previousRecords, reportRecordsByBucket, granularInterval,
        )

        val dataBuckets = granularInterval
            .getTimeBuckets()
            .map { timeBucket ->
                ExpenseReportDataBucket(
                    timeBucket = timeBucket,
                    amount = reportRecordsByBucket[timeBucket]
                        ?.filter { it.labels.any { label -> label in reportView.labels } }
                        ?.sumOf { it.reportCurrencyAmount }
                        ?: BigDecimal.ZERO,
                    startValue = valuesReports[timeBucket]?.startValue ?: BigDecimal.ZERO,
                    endValue = valuesReports[timeBucket]?.endValue ?: BigDecimal.ZERO,
                    minValue = BigDecimal.ZERO,
                    maxValue = BigDecimal.ZERO,
                )
            }

        return ExpenseReportData(reportViewId, granularInterval, dataBuckets)
    }

    private fun getValueReports(
        previousRecords: List<ReportRecord>,
        reportRecordsByBucket: Map<LocalDate, List<ReportRecord>>,
        granularInterval: GranularDateInterval,
    ): Map<LocalDate, ValueReport> {
        val previousValue = previousRecords
            // TODO(Johann) questionable if right
            .sumOf { it.reportCurrencyAmount }

        val nextValueReport: (LocalDate, ValueReport) -> ValueReport = { date, previousValueReport ->
            val bucket = reportRecordsByBucket[date] ?: emptyList()
            ValueReport(
                startValue = previousValueReport.endValue,
                endValue = previousValueReport.endValue + bucket.sumOf { it.reportCurrencyAmount },
                minValue = BigDecimal.ZERO,
                maxValue = BigDecimal.ZERO,
            )
        }

        return generateTimeBucketedData(
            granularInterval,
            { date -> nextValueReport(date, ValueReport(endValue = previousValue)) },
            nextValueReport,
        )
            .associateBy { it.first }
            .mapValues { it.value.second }
    }

    private fun splitRecordsInBeforeAndDuringInterval(
        records: List<ReportRecord>,
        interval: GranularDateInterval,
    ): Pair<List<ReportRecord>, List<ReportRecord>> {
        return records.partition { it.date < interval.interval.from }
    }
}