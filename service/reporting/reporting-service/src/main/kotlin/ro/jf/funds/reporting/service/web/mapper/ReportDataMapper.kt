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
                amount = dataItem.aggregate.amount,
                value = dataItem.aggregate.value?.let {
                    ValueReportTO(start = it.start, end = it.end, min = it.min, max = it.max)
                },
            )
        }
    )
}
