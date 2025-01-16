package ro.jf.funds.reporting.api

import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.ReportDataTO
import java.util.*

interface ReportDataApi {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularDateInterval: GranularDateInterval
    ): ReportDataTO
}
