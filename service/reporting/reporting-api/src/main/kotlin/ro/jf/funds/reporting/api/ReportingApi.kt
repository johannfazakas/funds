package ro.jf.funds.reporting.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.*
import java.util.*

interface ReportingApi {
    suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTO
    suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO>
    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO

    suspend fun getNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<NetReportTO>

    suspend fun getGroupedNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ByGroupTO<GroupNetReportTO>>

    suspend fun getValueData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ValueReportTO>

    suspend fun getGroupedBudgetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ByGroupTO<GroupedBudgetReportTO>>

    suspend fun getPerformanceData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<PerformanceReportTO>
}
