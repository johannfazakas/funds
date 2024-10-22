package ro.jf.funds.reporting.api

import ro.jf.funds.reporting.api.model.ReportViewDataTO
import java.util.*

interface ReportViewDataApi {
    suspend fun getReportViewData(userId: UUID, reportViewId: UUID): ReportViewDataTO
}
