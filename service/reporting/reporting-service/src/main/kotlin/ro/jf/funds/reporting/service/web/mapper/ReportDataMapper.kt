package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.BucketType
import ro.jf.funds.reporting.service.domain.ReportData

fun ReportData.toTO(): ReportDataTO {
    return ReportDataTO(
        viewId = this.reportViewId,
        granularInterval = granularInterval,
        data = data.map { dataItem ->
            ReportDataItemTO(
                timeBucket = dataItem.timeBucket,
                bucketType = dataItem.bucketType.toTO(),
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

fun BucketType.toTO(): BucketTypeTO = when (this) {
    BucketType.REAL -> BucketTypeTO.REAL
    BucketType.FORECAST -> BucketTypeTO.FORECAST
}
