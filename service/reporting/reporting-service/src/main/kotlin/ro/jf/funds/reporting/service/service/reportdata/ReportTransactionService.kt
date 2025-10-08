package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.reporting.service.domain.*
import java.util.*

class ReportTransactionService(
    private val transactionSdk: TransactionSdk,
) {
    suspend fun getPreviousReportTransactions(
        reportView: ReportView,
        interval: ReportDataInterval,
    ): List<ReportTransaction> = withSuspendingSpan {
        getReportTransactions(reportView.userId, reportView.fundId, null, interval.getPreviousLastDay())
    }

    suspend fun getBucketReportTransactions(
        reportView: ReportView,
        timeBucket: TimeBucket,
    ): List<ReportTransaction> = withSuspendingSpan {
        getReportTransactions(reportView.userId, reportView.fundId, timeBucket.from, timeBucket.to)
    }

    private suspend fun getReportTransactions(
        userId: UUID,
        fundId: UUID,
        fromDate: LocalDate?,
        toDate: LocalDate,
    ): List<ReportTransaction> {
        val filter = TransactionFilterTO(fromDate, toDate, fundId)
        return transactionSdk
            .listTransactions(userId, filter).items
            .asSequence()
            .map { it.toReportTransaction() }
            .toList()
    }

    private fun TransactionTO.toReportTransaction(): ReportTransaction {
        return ReportTransaction(
            date = this.dateTime.date,
            records = this.records.map { record ->
                ReportRecord(
                    date = this.dateTime.date,
                    fundId = record.fundId,
                    unit = record.unit,
                    amount = record.amount,
                    labels = record.labels,
                )
            },
        )
    }
}