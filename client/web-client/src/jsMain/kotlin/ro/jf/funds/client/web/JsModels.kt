package ro.jf.funds.client.web

import ro.jf.funds.reporting.api.model.ReportDataIntervalTO
import ro.jf.funds.reporting.api.model.YearMonthTO
import kotlin.js.JsExport

@JsExport
data class JsUser(
    val id: String,
    val username: String
)

@JsExport
data class JsFund(
    val id: String,
    val name: String
)

@JsExport
data class JsReportView(
    val id: String,
    val name: String,
)

@JsExport
data class JsGroupedBudgetReport(
    val viewId: String,
    val timeBuckets: Array<JsBucketData>,
)

@JsExport
data class JsBucketData(
    val label: String,
    val bucketType: String,
    val groups: Array<JsGroupBudget>,
)

@JsExport
data class JsGroupBudget(
    val group: String,
    val allocated: Double,
    val spent: Double,
    val left: Double,
)

sealed class JsReportInterval {
    data class Monthly(
        val fromYearMonth: String,
        val toYearMonth: String,
        val forecastUntilYearMonth: String? = null,
    ) : JsReportInterval()

    data class Yearly(
        val fromYear: Int,
        val toYear: Int,
        val forecastUntilYear: Int? = null,
    ) : JsReportInterval()
}

internal fun JsReportInterval.toReportDataIntervalTO(): ReportDataIntervalTO = when (this) {
    is JsReportInterval.Monthly -> ReportDataIntervalTO.Monthly(
        fromYearMonth = YearMonthTO.parse(fromYearMonth),
        toYearMonth = YearMonthTO.parse(toYearMonth),
        forecastUntilYearMonth = forecastUntilYearMonth?.let { YearMonthTO.parse(it) },
    )
    is JsReportInterval.Yearly -> ReportDataIntervalTO.Yearly(
        fromYear = fromYear,
        toYear = toYear,
        forecastUntilYear = forecastUntilYear,
    )
}
