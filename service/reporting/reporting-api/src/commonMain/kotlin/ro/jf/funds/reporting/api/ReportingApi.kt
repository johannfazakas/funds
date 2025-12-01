package ro.jf.funds.reporting.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.commons.api.model.ListTO
import ro.jf.funds.reporting.api.model.*

interface ReportingApi {
    suspend fun createReportView(userId: Uuid, request: CreateReportViewTO): ReportViewTO
    suspend fun listReportViews(userId: Uuid): ListTO<ReportViewTO>
    suspend fun getReportView(userId: Uuid, reportViewId: Uuid): ReportViewTO

    suspend fun getNetData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<NetReportTO>

    suspend fun getGroupedNetData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ByGroupTO<GroupNetReportTO>>

    suspend fun getValueData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ValueReportTO>

    suspend fun getGroupedBudgetData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<ByGroupTO<GroupedBudgetReportTO>>

    suspend fun getPerformanceData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<PerformanceReportTO>

    suspend fun getInstrumentPerformanceData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<InstrumentsPerformanceReportTO>

    suspend fun getInterestRateData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<InterestRateReportTO>

    suspend fun getInstrumentInterestRateData(
        userId: Uuid,
        reportViewId: Uuid,
        reportDataInterval: ReportDataIntervalTO,
    ): ReportDataTO<InstrumentsInterestRateReportTO>
}
