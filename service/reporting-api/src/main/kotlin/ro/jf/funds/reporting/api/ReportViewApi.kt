package ro.jf.funds.reporting.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.CreateReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import java.util.*

interface ReportViewApi {
    suspend fun createReport(userId: UUID, request: CreateReportViewTO): CreateReportViewTaskTO
    suspend fun getReportViewTask(userId: UUID, taskId: UUID): CreateReportViewTaskTO
    suspend fun getReport(userId: UUID, reportId: UUID): ReportViewTO
    suspend fun listReports(userId: UUID): ListTO<ReportViewTO>
}
