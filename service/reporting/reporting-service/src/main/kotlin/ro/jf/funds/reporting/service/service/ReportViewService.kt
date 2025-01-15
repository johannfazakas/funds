package ro.jf.funds.reporting.service.service

import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularTimeInterval
import ro.jf.funds.reporting.service.domain.ReportData
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportingException
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.util.*

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val fundTransactionSdk: FundTransactionSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
        val transactions = fundTransactionSdk.listTransactions(userId, payload.fundId)
        transactions.items.size
        return reportViewRepository.create(userId, payload.name, payload.fundId, payload.type)
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView {
        return reportViewRepository.findById(userId, reportViewId) ?: throw ReportingException.ReportViewNotFound()
    }

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularTimeInterval,
    ): ReportData {
        val reportView =
            reportViewRepository.findById(userId, reportViewId) ?: throw ReportingException.ReportViewNotFound()
        return ReportData(reportViewId, reportView.name, reportView.fundId, granularInterval, emptyList())
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> {
        return reportViewRepository.findAll(userId)
    }
}
