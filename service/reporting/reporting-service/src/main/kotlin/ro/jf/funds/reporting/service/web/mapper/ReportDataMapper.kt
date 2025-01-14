package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ExpenseReportDataTO
import ro.jf.funds.reporting.api.model.ReportDataTO
import ro.jf.funds.reporting.service.domain.ReportData

fun ReportData.toTO(): ReportDataTO {
    return ExpenseReportDataTO(
        viewId = this.reportViewId,
        viewName = reportViewName,
        fundId = fundId,
        granularInterval = granularInterval,
        data = emptyList() // TODO(Johann) this should also be serialized
    )
}
