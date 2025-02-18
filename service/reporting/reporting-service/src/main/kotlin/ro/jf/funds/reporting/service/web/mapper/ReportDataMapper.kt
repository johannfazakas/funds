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
                timeBucket = dataItem.timeBucket.from,
                amount = dataItem.aggregate.amount,
                value = ValueReportTO(
                    start = dataItem.aggregate.value.start,
                    end = dataItem.aggregate.value.end,
                    min = dataItem.aggregate.value.min,
                    max = dataItem.aggregate.value.max
                ),
            )
        }
    )
}
