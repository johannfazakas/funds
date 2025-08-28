package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*

fun <D, TO> ReportData<D>.toTO(itemMapper: (D) -> TO): ReportDataTO<TO> {
    return ReportDataTO(
        viewId = this.reportViewId,
        interval = this.interval.toDate(),
        data = data.map { dataItem: BucketData<D> ->
            ReportDataItemTO(
                timeBucket = DateIntervalTO(dataItem.timeBucket.from, dataItem.timeBucket.to),
                bucketType = dataItem.bucketType.toTO(),
                data = itemMapper(dataItem.data)
            )
        }
    )
}

fun ReportDataInterval.toDate(): ReportDataIntervalTO =
    when (this) {
        is ReportDataInterval.Yearly -> ReportDataIntervalTO.Yearly(this.fromYear, this.toYear, this.forecastUntilYear)
        is ReportDataInterval.Monthly -> ReportDataIntervalTO.Monthly(
            this.fromYearMonth.toTO(),
            this.toYearMonth.toTO(),
            this.forecastUntilYearMonth?.toTO()
        )

        is ReportDataInterval.Daily -> ReportDataIntervalTO.Daily(this.fromDate, this.toDate, this.forecastUntilDate)
    }

fun YearMonth.toTO() = YearMonthTO(this.year, this.month)

fun ReportDataAggregate.toTO(): ReportDataAggregateTO =
    ReportDataAggregateTO(
        net = this.net,
        value = this.value?.let {
            ValueReportTO(start = it.start, end = it.end, min = it.min, max = it.max)
        },
        groupedNet = this.run {
            groupedNet?.map { (group, net) ->
                ReportDataGroupedNetItemTO(
                    group = group,
                    net = net
                )
            }
        },
        groupedBudget = this.run {
            groupedBudget?.map { (group, budget) ->
                ReportDataGroupedBudgetItemTO(
                    group = group,
                    allocated = budget.allocated,
                    spent = budget.spent,
                    left = budget.left
                )
            }
        },
        performance = this.performance?.let {
            PerformanceReportTO(
                totalAssetsValue = it.totalAssetsValue,
                totalCurrencyValue = it.totalCurrencyValue,
                totalInvestment = it.totalInvestment,
                currentInvestment = it.currentInvestment,
                totalProfit = it.totalProfit,
                currentProfit = it.currentProfit,
            )
        }
    )

fun BucketType.toTO(): BucketTypeTO = when (this) {
    BucketType.REAL -> BucketTypeTO.REAL
    BucketType.FORECAST -> BucketTypeTO.FORECAST
}
