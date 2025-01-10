package ro.jf.funds.reporting.service.service

import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularTimeInterval
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportViewData
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.util.*

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
        // TODO(Johann) what should be here?
        return reportViewRepository.create(userId, payload.name, payload.fundId, payload.type)
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView {
        return reportViewRepository.findById(userId, reportViewId) ?: error("Report view not found")
    }

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularTimeInterval,
    ): ReportViewData {
        TODO()
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> {
        return reportViewRepository.findAll(userId)
    }
}
