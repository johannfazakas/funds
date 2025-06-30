package ro.jf.funds.reporting.api

import ro.jf.funds.reporting.api.model.ReportDataIntervalTO
import ro.jf.funds.reporting.api.model.ReportDataTO
import java.util.*

interface ReportDataApi {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularDateInterval: ReportDataIntervalTO
    ): ReportDataTO
}
