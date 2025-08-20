package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*

fun ReportData.toTO(): ReportDataTO {
    return ReportDataTO(
        viewId = this.reportViewId,
        interval = ReportDataIntervalTO(
            granularity = when (this.interval) {
                is ReportDataInterval.Yearly -> TimeGranularityTO.YEARLY
                is ReportDataInterval.Monthly -> TimeGranularityTO.MONTHLY
                is ReportDataInterval.Daily -> TimeGranularityTO.DAILY
            },
            fromDate = this.interval.fromDate,
            toDate = this.interval.toDate,
            forecastUntilDate = this.interval.forecastUntilDate,
        ),
        data = data.map { dataItem: BucketData<ReportDataAggregate> ->
            ReportDataItemTO(
                timeBucket = DateIntervalTO(dataItem.timeBucket.from, dataItem.timeBucket.to),
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
                },
                performance = dataItem.aggregate.performance?.let {
                    PerformanceReportTO(
                        totalAssetsValue = it.totalAssetsValue,
                        totalInvestment = it.totalInvestment,
                        currentInvestment = it.currentInvestment,
                        totalProfit = it.totalProfit,
                        currentProfit = it.currentProfit,
                    )
                }
            )
        }
    )
}

fun BucketType.toTO(): BucketTypeTO = when (this) {
    BucketType.REAL -> BucketTypeTO.REAL
    BucketType.FORECAST -> BucketTypeTO.FORECAST
}
