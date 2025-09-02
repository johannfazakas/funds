package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*

fun <D, TO> ReportData<D>.toTO(itemMapper: (D) -> TO): ReportDataTO<TO> {
    return ReportDataTO(
        viewId = this.reportViewId,
        interval = this.interval.toDate(),
        timeBuckets = buckets.map { dataItem: BucketData<D> ->
            BucketDataTO(
                timeBucket = DateIntervalTO(dataItem.timeBucket.from, dataItem.timeBucket.to),
                bucketType = dataItem.bucketType.toBucketTypeTO(),
                report = itemMapper(dataItem.report)
            )
        }
    )
}

fun ReportDataInterval.toDate(): ReportDataIntervalTO =
    when (this) {
        is ReportDataInterval.Yearly -> ReportDataIntervalTO.Yearly(this.fromYear, this.toYear, this.forecastUntilYear)
        is ReportDataInterval.Monthly -> ReportDataIntervalTO.Monthly(
            this.fromYearMonth.toYearMonthTO(),
            this.toYearMonth.toYearMonthTO(),
            this.forecastUntilYearMonth?.toYearMonthTO()
        )

        is ReportDataInterval.Daily -> ReportDataIntervalTO.Daily(this.fromDate, this.toDate, this.forecastUntilDate)
    }

fun YearMonth.toYearMonthTO() = YearMonthTO(this.year, this.month)

fun BucketType.toBucketTypeTO(): BucketTypeTO = when (this) {
    BucketType.REAL -> BucketTypeTO.REAL
    BucketType.FORECAST -> BucketTypeTO.FORECAST
}

fun NetReport.toNetReportTO(): NetReportTO = NetReportTO(this.net)

fun ByGroup<NetReport>.toGroupedNetTO(): GroupedTO<GroupNetReportTO> =
    this.map { (group, report) -> GroupNetReportTO(group = group, net = report.net) }
        .let(::GroupedTO)

fun ValueReport.toValueReportTO(): ValueReportTO =
    ValueReportTO(
        start = this.start,
        end = this.end,
        min = this.min,
        max = this.max
    )

fun ByGroup<Budget>.toGroupedBudgetTO(): GroupedTO<GroupedBudgetReportTO> =
    this.map { (group, budget) ->
        GroupedBudgetReportTO(
            group = group,
            allocated = budget.allocated,
            spent = budget.spent,
            left = budget.left
        )
    }
        .let(::GroupedTO)

fun PerformanceReport.toPerformanceTO(): PerformanceReportTO =
    PerformanceReportTO(
        totalAssetsValue = this.totalAssetsValue,
        totalCurrencyValue = this.totalCurrencyValue,
        totalInvestment = this.totalInvestment,
        currentInvestment = this.currentInvestment,
        totalProfit = this.totalProfit,
        currentProfit = this.currentProfit
    )
