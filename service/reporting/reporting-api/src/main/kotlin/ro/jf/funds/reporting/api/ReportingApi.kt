package ro.jf.funds.reporting.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.*
import java.util.*

interface ReportingApi {
    suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTaskTO
    suspend fun getReportViewTask(userId: UUID, taskId: UUID): ReportViewTaskTO
    suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO>
    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO
    suspend fun getReportViewData(
        userId: UUID, reportViewId: UUID, granularInterval: GranularDateInterval,
    ): ReportDataTO
}
