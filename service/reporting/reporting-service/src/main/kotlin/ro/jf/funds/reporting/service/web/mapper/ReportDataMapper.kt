package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ReportDataGroupItemTO
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
                net = dataItem.aggregate.net,
                value = dataItem.aggregate.value?.let {
                    ValueReportTO(start = it.start, end = it.end, min = it.min, max = it.max)
                },
                groups = dataItem.aggregate.run {
                    (groupedNet ?: groupedBudget)
                        ?.map { (group, _) -> group }
                        ?.map { group ->
                            ReportDataGroupItemTO(
                                group = group,
                                net = this.groupedNet?.get(group),
                                allocated = this.groupedBudget?.get(group)?.allocated,
                                left = this.groupedBudget?.get(group)?.left
                            )
                        }
                }
            )
        }
    )
}
