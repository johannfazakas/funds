package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.service.utils.toKmpBigDecimal
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

fun NetReport.toNetReportTO(): NetReportTO = NetReportTO(this.net.toKmpBigDecimal())

fun ByGroup<NetReport>.toGroupedNetTO(): ByGroupTO<GroupNetReportTO> =
    this.map { (group, report) -> GroupNetReportTO(group = group, net = report.net.toKmpBigDecimal()) }
        .let(::ByGroupTO)

fun ValueReport.toValueReportTO(): ValueReportTO =
    ValueReportTO(
        start = this.start.toKmpBigDecimal(),
        end = this.end.toKmpBigDecimal(),
        min = this.min.toKmpBigDecimal(),
        max = this.max.toKmpBigDecimal()
    )

fun ByGroup<Budget>.toGroupedBudgetTO(): ByGroupTO<GroupedBudgetReportTO> =
    this.map { (group, budget) ->
        GroupedBudgetReportTO(
            group = group,
            allocated = budget.allocated.toKmpBigDecimal(),
            spent = budget.spent.toKmpBigDecimal(),
            left = budget.left.toKmpBigDecimal()
        )
    }
        .let(::ByGroupTO)

fun PerformanceReport.toPerformanceTO(): PerformanceReportTO =
    PerformanceReportTO(
        totalAssetsValue = this.totalAssetsValue.toKmpBigDecimal(),
        totalCurrencyValue = this.totalCurrencyValue.toKmpBigDecimal(),
        totalInvestment = this.totalInvestment.toKmpBigDecimal(),
        currentInvestment = this.currentInvestment.toKmpBigDecimal(),
        totalProfit = this.totalProfit.toKmpBigDecimal(),
        currentProfit = this.currentProfit.toKmpBigDecimal()
    )

fun ByInstrument<InstrumentPerformanceReport>.toInstrumentsPerformanceReportTO(): InstrumentsPerformanceReportTO =
    this.map { (symbol, performance) ->
        InstrumentPerformanceReportTO(
            instrument = symbol,
            totalUnits = performance.totalUnits.toKmpBigDecimal(),
            currentUnits = performance.currentUnits.toKmpBigDecimal(),
            totalValue = performance.totalValue.toKmpBigDecimal(),
            totalInvestment = performance.totalInvestment.toKmpBigDecimal(),
            currentInvestment = performance.currentInvestment.toKmpBigDecimal(),
            totalProfit = performance.totalProfit.toKmpBigDecimal(),
            currentProfit = performance.currentProfit.toKmpBigDecimal()
        )
    }
        .let(::InstrumentsPerformanceReportTO)

fun InterestRateReport.toInterestRateTO(): InterestRateReportTO =
    InterestRateReportTO(
        totalInterestRate = this.totalInterestRate.toKmpBigDecimal(),
        currentInterestRate = this.currentInterestRate.toKmpBigDecimal()
    )

fun ByInstrument<InstrumentInterestRateReport>.toInstrumentsInterestRateTO(): InstrumentsInterestRateReportTO =
    this.map { (instrument, interestRate) ->
        InstrumentInterestRateReportTO(
            instrument = instrument,
            totalInterestRate = interestRate.totalInterestRate.toKmpBigDecimal(),
            currentInterestRate = interestRate.currentInterestRate.toKmpBigDecimal()
        )
    }
        .let(::InstrumentsInterestRateReportTO)
