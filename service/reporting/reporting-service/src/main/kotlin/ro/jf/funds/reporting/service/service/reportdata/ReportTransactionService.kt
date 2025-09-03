package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.service.domain.ReportDataInterval
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.TimeBucket
import java.util.*

class ReportTransactionService(
    private val fundTransactionSdk: FundTransactionSdk,
) {
    suspend fun getPreviousReportRecords(
        reportView: ReportView,
        interval: ReportDataInterval,
    ): List<ReportRecord> = withSuspendingSpan {
        getReportRecords(reportView.userId, reportView.fundId, null, interval.getPreviousLastDay())
    }

    suspend fun getBucketReportRecords(
        reportView: ReportView,
        timeBucket: TimeBucket,
    ): List<ReportRecord> = withSuspendingSpan {
        getReportRecords(reportView.userId, reportView.fundId, timeBucket.from, timeBucket.to)
    }

    private suspend fun getReportRecords(
        userId: UUID,
        fundId: UUID,
        fromDate: LocalDate?,
        toDate: LocalDate,
    ): List<ReportRecord> {
        val filter = FundTransactionFilterTO(fromDate, toDate)
        return fundTransactionSdk
            .listTransactions(userId, fundId, filter).items
            .asSequence()
            .flatMap { it.toReportRecords(fundId) }
            .toList()
    }

    private fun FundTransactionTO.toReportRecords(fundId: UUID): List<ReportRecord> {
        return this.records
            .filter { record -> record.fundId == fundId }
            .map { record ->
                ReportRecord(
                    transactionId = this.id,
                    date = this.dateTime.date,
                    unit = record.unit,
                    amount = record.amount,
                    labels = record.labels,
                )
            }
    }
}