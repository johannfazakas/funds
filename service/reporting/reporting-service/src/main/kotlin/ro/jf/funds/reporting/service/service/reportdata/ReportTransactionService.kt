package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.service.domain.*
import java.util.*

class ReportTransactionService(
    private val fundTransactionSdk: FundTransactionSdk,
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
        val filter = FundTransactionFilterTO(fromDate, toDate)
        return fundTransactionSdk
            .listTransactions(userId, fundId, filter).items
            .asSequence()
            .map { it.toReportTransaction() }
            .toList()
    }

    private fun FundTransactionTO.toReportTransaction(): ReportTransaction {
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