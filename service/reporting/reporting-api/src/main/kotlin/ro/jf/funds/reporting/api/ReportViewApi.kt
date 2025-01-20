package ro.jf.funds.reporting.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import java.util.*

interface ReportViewApi {
    suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTaskTO
    suspend fun getReportViewTask(userId: UUID, taskId: UUID): ReportViewTaskTO
    suspend fun getReportView(userId: UUID, reportId: UUID): ReportViewTO
    suspend fun listReportsViews(userId: UUID): ListTO<ReportViewTO>
}
