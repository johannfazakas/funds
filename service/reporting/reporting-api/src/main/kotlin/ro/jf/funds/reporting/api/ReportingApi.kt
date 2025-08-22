package ro.jf.funds.reporting.api

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.reporting.api.model.*
import java.util.*

interface ReportingApi {
    suspend fun createReportView(userId: UUID, request: CreateReportViewTO): ReportViewTO
    suspend fun listReportViews(userId: UUID): ListTO<ReportViewTO>
    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportViewTO

    suspend fun getYearlyReportViewData(
        userId: UUID,
        reportViewId: UUID,
        fromYear: Int,
        toYear: Int,
        forecastUntilYear: Int? = null,
    ): ReportDataTO<ReportDataAggregateTO>

    suspend fun getMonthlyReportViewData(
        userId: UUID,
        reportViewId: UUID,
        fromYearMonth: YearMonthTO,
        toYearMonth: YearMonthTO,
        forecastUntilYearMonth: YearMonthTO? = null,
    ): ReportDataTO<ReportDataAggregateTO>

    suspend fun getDailyReportViewData(
        userId: UUID,
        reportViewId: UUID,
        fromDate: LocalDate,
        toDate: LocalDate,
        forecastUntilDate: LocalDate? = null,
    ): ReportDataTO<ReportDataAggregateTO>
}
