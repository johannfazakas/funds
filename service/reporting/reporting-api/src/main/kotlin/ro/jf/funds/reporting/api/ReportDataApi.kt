package ro.jf.funds.reporting.api

import ro.jf.funds.reporting.api.model.GranularTimeIntervalTO
import ro.jf.funds.reporting.api.model.ReportViewDataTO
import java.util.*

interface ReportDataApi {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularTimeInterval: GranularTimeIntervalTO
    ): ReportViewDataTO
}
