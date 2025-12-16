package ro.jf.funds.reporting.service.service.reportdata

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionRecordTO
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
        val date = this.dateTime.date
        return when (this) {
            is TransactionTO.SingleRecord -> ReportTransaction.SingleRecord(
                date = date,
                record = this.record.toReportRecord(date),
            )
            is TransactionTO.Transfer -> ReportTransaction.Transfer(
                date = date,
                sourceRecord = this.sourceRecord.toReportRecord(date),
                destinationRecord = this.destinationRecord.toReportRecord(date),
            )
            is TransactionTO.Exchange -> ReportTransaction.Exchange(
                date = date,
                sourceRecord = this.sourceRecord.toReportRecord(date),
                destinationRecord = this.destinationRecord.toReportRecord(date),
                feeRecord = this.feeRecord?.toReportRecord(date),
            )
            is TransactionTO.OpenPosition -> ReportTransaction.OpenPosition(
                date = date,
                currencyRecord = this.currencyRecord.toReportRecord(date),
                instrumentRecord = this.instrumentRecord.toReportRecord(date),
            )
            is TransactionTO.ClosePosition -> ReportTransaction.ClosePosition(
                date = date,
                currencyRecord = this.currencyRecord.toReportRecord(date),
                instrumentRecord = this.instrumentRecord.toReportRecord(date),
            )
        }
    }

    private fun TransactionRecordTO.toReportRecord(date: LocalDate): ReportRecord {
        return ReportRecord(
            date = date,
            fundId = this.fundId,
            unit = this.unit,
            amount = this.amount.toJavaBigDecimal(),
            labels = this.labels,
        )
    }
}