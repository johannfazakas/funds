package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.MetricInfoTO
import ro.jf.funds.analytics.api.model.MetricSeriesGroupTO
import ro.jf.funds.analytics.api.model.MetricSeriesTO
import ro.jf.funds.analytics.api.model.MetricTO
import ro.jf.funds.analytics.api.model.MetricUnitTypeTO
import ro.jf.funds.analytics.api.model.MetricsReportRequestTO
import ro.jf.funds.analytics.api.model.MetricsReportTO
import ro.jf.funds.analytics.service.domain.AnalyticsInputRecordFilter
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.MetricResolutionReport
import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.service.MetricResolutionService
import ro.jf.funds.platform.jvm.web.userId

private val log = logger { }

fun Routing.metricsApiRouting(
    metricResolutionService: MetricResolutionService,
) {
    route("/funds-api/analytics/v1/metrics") {
        get {
            call.respond(
                HttpStatusCode.OK,
                MetricTO.entries.map { MetricInfoTO(metric = it, unit = it.unit) },
            )
        }
        post {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<MetricsReportRequestTO>()
            log.info { "Metrics report request for user $userId: $request" }
            val report = metricResolutionService.resolve(
                MetricResolutionRequest(
                    userId = userId,
                    interval = ReportInterval(request.interval.granularity, request.interval.from, request.interval.to),
                    filter = AnalyticsInputRecordFilter(
                        fundIds = request.filter.fundIds,
                        units = request.filter.units,
                    ),
                    targetCurrency = request.targetCurrency,
                    grouping = request.grouping,
                    metrics = request.metrics.map { Series.of(it) },
                )
            )
            call.respond(HttpStatusCode.OK, report.toTO(request))
        }
    }
}

private fun MetricResolutionReport.toTO(request: MetricsReportRequestTO): MetricsReportTO = MetricsReportTO(
    granularity = request.interval.granularity,
    buckets = buckets,
    series = series.map { (metric, scalarSeries) ->
        MetricSeriesTO(
            metric = metric.api,
            unit = metric.api.unit,
            currency = request.targetCurrency.takeIf { metric.api.unit == MetricUnitTypeTO.CURRENCY },
            groups = scalarSeries.groupKeys
                .sortedBy { it.apiValue }
                .map { groupKey ->
                    MetricSeriesGroupTO(
                        groupKey = groupKey.apiValue,
                        values = buckets.map { bucket -> scalarSeries[bucket, groupKey] },
                    )
                },
        )
    },
)
