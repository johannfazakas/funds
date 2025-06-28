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
                groupedNet = dataItem.aggregate.run {
                    groupedNet?.map { (group, net) ->
                        ReportDataGroupedNetItemTO(
                            group = group,
                            net = net
                        )
                    }
                },
                groupedBudget = dataItem.aggregate.run {
                    groupedBudget?.map { (group, budget) ->
                        ReportDataGroupedBudgetItemTO(
                            group = group,
                            allocated = budget.allocated,
                            spent = budget.spent,
                            left = budget.left
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
