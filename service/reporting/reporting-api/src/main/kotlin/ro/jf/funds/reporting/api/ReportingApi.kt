package ro.jf.funds.reporting.api

import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.*
import java.util.*

interface ReportingApi {
    suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTO
    suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO>
    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ReportDataAggregateTO>

    suspend fun getNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ReportDataNetItemTO>

    suspend fun getGroupedNetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<List<ReportDataGroupedNetItemTO>>

    suspend fun getValueData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ValueReportItemTO>

    // TODO(Johann) think again, should T be a List here?
    suspend fun getGroupedBudgetData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<List<ReportDataGroupedBudgetItemTO>>

    suspend fun getPerformanceData(
        userId: UUID,
        reportViewId: UUID,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<PerformanceReportTO>
}
