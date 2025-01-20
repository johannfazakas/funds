package ro.jf.funds.reporting.service.service

import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.getTimeBucket
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.*

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val fundTransactionSdk: FundTransactionSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
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
        return reportViewRepository.findById(userId, reportViewId) ?: throw ReportingException.ReportViewNotFound()
    }

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData {
        reportViewRepository.findById(userId, reportViewId) ?: throw ReportingException.ReportViewNotFound()

        val reportRecordsByBucket = reportRecordRepository
            .findByViewInInterval(userId, reportViewId, granularInterval.interval)
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
