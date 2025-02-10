package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ReportDataItemTO
import ro.jf.funds.reporting.api.model.ReportDataTO
import ro.jf.funds.reporting.api.model.ValueReportTO
import ro.jf.funds.reporting.service.domain.ReportData

fun ReportData.toTO(): ReportDataTO {
    return ReportDataTO(
        viewId = this.reportViewId,
        granularInterval = granularInterval,
        data = data.map { dataItem ->
            ReportDataItemTO(
                timeBucket = dataItem.timeBucket,
                amount = dataItem.amount,
                value = ValueReportTO(
                    start = dataItem.value.start,
                    end = dataItem.value.end,
                    min = dataItem.value.min,
                    max = dataItem.value.max
                ),
            )
        }
    )
}
