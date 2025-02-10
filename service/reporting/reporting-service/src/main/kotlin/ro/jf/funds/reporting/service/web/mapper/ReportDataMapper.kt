package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ExpenseReportDataTO
import ro.jf.funds.reporting.api.model.ReportDataTO
import ro.jf.funds.reporting.service.domain.ExpenseReportData
import ro.jf.funds.reporting.service.domain.ReportData

fun ReportData.toTO(): ReportDataTO {
    return when (this) {
        is ExpenseReportData ->
            ExpenseReportDataTO(
                viewId = this.reportViewId,
                granularInterval = granularInterval,
                data = data.map { dataItem ->
                    ExpenseReportDataTO.DataItem(
                        timeBucket = dataItem.timeBucket,
                        amount = dataItem.amount,
                        startValue = dataItem.startValue,
                        endValue = dataItem.endValue,
                        minValue = dataItem.minValue,
                        maxValue = dataItem.maxValue
                    )
                }
            )
    }
}
