package ro.jf.funds.reporting.service.service.reportdata

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.Category
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.sdk.CategorySdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.reporting.service.domain.*
import java.util.*

class ReportTransactionService(
    private val transactionSdk: TransactionSdk,
    private val categorySdk: CategorySdk,
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
        val categoryMap = categorySdk.listCategories(userId).associateBy { it.id }
        return transactionSdk
            .listTransactions(userId, filter).items
            .asSequence()
            .mapNotNull { it.toReportTransaction(fundId, categoryMap) }
            .toList()
    }

    private fun TransactionTO.toReportTransaction(fundId: UUID, categoryMap: Map<Uuid, CategoryTO>): ReportTransaction? {
        val date = this.dateTime.date
        return when (this) {
            is TransactionTO.SingleRecord -> {
                if (this.record.fundId != fundId) return null
                ReportTransaction.SingleRecord(
                    date = date,
                    record = this.record.toReportRecord(date, categoryMap),
                )
            }
            is TransactionTO.Transfer -> {
                val source = this.sourceRecord.takeIf { it.fundId == fundId }?.toReportRecord(date, categoryMap)
                val dest = this.destinationRecord.takeIf { it.fundId == fundId }?.toReportRecord(date, categoryMap)
                if (source == null && dest == null) return null
                ReportTransaction.Transfer(
                    date = date,
                    sourceRecord = source,
                    destinationRecord = dest,
                )
            }
            is TransactionTO.Exchange -> {
                val source = this.sourceRecord.takeIf { it.fundId == fundId }?.toReportRecord(date, categoryMap)
                val dest = this.destinationRecord.takeIf { it.fundId == fundId }?.toReportRecord(date, categoryMap)
                val fee = this.feeRecord?.takeIf { it.fundId == fundId }?.toReportRecord(date, categoryMap)
                if (source == null && dest == null && fee == null) return null
                ReportTransaction.Exchange(
                    date = date,
                    sourceRecord = source,
                    destinationRecord = dest,
                    feeRecord = fee,
                )
            }
            is TransactionTO.OpenPosition -> ReportTransaction.OpenPosition(
                date = date,
                currencyRecord = this.currencyRecord.toReportRecord(date, categoryMap),
                instrumentRecord = this.instrumentRecord.toReportRecord(date, categoryMap),
            )
            is TransactionTO.ClosePosition -> ReportTransaction.ClosePosition(
                date = date,
                currencyRecord = this.currencyRecord.toReportRecord(date, categoryMap),
                instrumentRecord = this.instrumentRecord.toReportRecord(date, categoryMap),
            )
        }
    }

    private fun TransactionRecordTO.toReportRecord(date: LocalDate, categoryMap: Map<Uuid, CategoryTO>): ReportRecord {
        return ReportRecord(
            date = date,
            fundId = this.fundId,
            unit = this.unit,
            amount = this.amount.toJavaBigDecimal(),
            category = this.categoryId?.let { categoryMap[it]?.name?.let(::Category) },
        )
    }
}
