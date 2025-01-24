package ro.jf.funds.reporting.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.getTimeBucket
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val fundTransactionSdk: FundTransactionSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
        log.info { "Create report view for user $userId: $payload" }

        reportViewRepository.findByName(userId, payload.name)?.let {
            throw ReportingException.ReportViewAlreadyExists(userId, payload.name)
        }
        val reportView = reportViewRepository.create(userId, payload.name, payload.fundId, payload.type)

        fundTransactionSdk.listTransactions(userId, payload.fundId).items
            .flatMap { transaction ->
                transaction.records
                    .filter { it.fundId == payload.fundId }
                    .map { CreateReportRecordCommand(userId, reportView.id, transaction.dateTime.date, it.amount) }
            }
            .forEach { reportRecordRepository.create(it) }

        return reportView
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView {
        return reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
    }

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData {
        // TODO(Johann) dive into logging a bit. how can it be controlled in a ktor service? This should probably be a DEBUG
        log.info { "Get report view data for user $userId, report $reportViewId and interval $granularInterval" }
        reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)

        val reportRecords = reportRecordRepository
            .findByViewInInterval(userId, reportViewId, granularInterval.interval)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval ${granularInterval.interval}" }
        val reportRecordsByBucket = reportRecords
            .groupBy { getTimeBucket(it.date, granularInterval.granularity) }

        val dataBuckets = granularInterval
            .getTimeBuckets()
            .map { timeBucket ->
                ExpenseReportDataBucket(
                    timeBucket,
                    reportRecordsByBucket[timeBucket]?.sumOf { it.amount } ?: BigDecimal.ZERO
                )
            }

        return ExpenseReportData(reportViewId, granularInterval, dataBuckets)
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> {
        return reportViewRepository.findAll(userId)
    }
}
