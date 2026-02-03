package ro.jf.funds.client.web.api

import com.benasher44.uuid.uuidFrom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import ro.jf.funds.client.sdk.ReportingClient
import ro.jf.funds.client.web.model.*
import ro.jf.funds.reporting.api.model.ByGroupTO
import ro.jf.funds.reporting.api.model.GroupedBudgetReportTO
import ro.jf.funds.reporting.api.model.ReportDataTO
import kotlin.js.JsExport
import kotlin.js.Promise

@JsExport
object ReportingApi {
    private val config = js("window.FUNDS_CONFIG")
    private val reportingServiceUrl: String = config?.reportingServiceUrl as? String ?: "http://localhost:5212"
    private val reportingClient = ReportingClient(baseUrl = reportingServiceUrl)

    fun listReportViews(userId: String): Promise<Array<JsReportView>> = GlobalScope.promise {
        val uuid = uuidFrom(userId)
        val views = reportingClient.listReportViews(uuid)
        views.map {
            JsReportView(
                id = it.id.toString(),
                name = it.name,
            )
        }.toTypedArray()
    }

    fun monthlyInterval(from: String, to: String, forecastUntil: String? = null): JsReportInterval =
        JsReportInterval.Monthly(from, to, forecastUntil)

    fun yearlyInterval(from: Int, to: Int, forecastUntil: Int? = null): JsReportInterval =
        JsReportInterval.Yearly(from, to, forecastUntil)

    fun getGroupedBudgetData(
        userId: String,
        reportViewId: String,
        interval: JsReportInterval,
    ): Promise<JsGroupedBudgetReport> = GlobalScope.promise {
        val data = reportingClient.getGroupedBudgetData(
            userId = uuidFrom(userId),
            reportViewId = uuidFrom(reportViewId),
            interval = interval.toReportDataIntervalTO(),
        )
        toJsGroupedBudgetReport(data)
    }

    private fun toJsGroupedBudgetReport(
        data: ReportDataTO<ByGroupTO<GroupedBudgetReportTO>>
    ): JsGroupedBudgetReport = JsGroupedBudgetReport(
        viewId = data.viewId.toString(),
        timeBuckets = data.timeBuckets.map { bucket ->
            JsBucketData(
                label = bucket.timeBucket.from.toString(),
                bucketType = bucket.bucketType.name,
                groups = bucket.report.groups.map { group ->
                    JsGroupBudget(
                        group = group.group,
                        allocated = group.allocated.doubleValue(false),
                        spent = group.spent.doubleValue(false),
                        left = group.left.doubleValue(false),
                    )
                }.toTypedArray()
            )
        }.toTypedArray()
    )
}
