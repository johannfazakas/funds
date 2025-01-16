package ro.jf.funds.reporting.service.service

import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.domain.ReportData
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportingException
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.util.*

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val fundTransactionSdk: FundTransactionSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
        val reportView = reportViewRepository.create(userId, payload.name, payload.fundId, payload.type)

        // TODO(Johann) this is just dummy logic for now.
        val transactions = fundTransactionSdk.listTransactions(userId, payload.fundId)
        val firstRecord = transactions.items.flatMap { it.records }.firstOrNull()
        firstRecord?.let {
            reportRecordRepository.create(userId, reportView.id, transactions.items.first().dateTime.date, it.amount)
        }

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
        val reportView =
            reportViewRepository.findById(userId, reportViewId) ?: throw ReportingException.ReportViewNotFound()
        return ReportData(reportViewId, reportView.name, reportView.fundId, granularInterval, emptyList())
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> {
        return reportViewRepository.findAll(userId)
    }
}
