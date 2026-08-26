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
import ro.jf.funds.analytics.service.domain.MetricQuery
import ro.jf.funds.analytics.service.domain.MetricResolutionReport
import ro.jf.funds.analytics.service.domain.MetricResolutionRequest
import ro.jf.funds.analytics.service.domain.QueryContext
import ro.jf.funds.analytics.service.domain.QueryId
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.Series
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
                    targetCurrency = request.targetCurrency,
                    queries = request.queries.map { query ->
                        MetricQuery(
                            id = QueryId(query.id),
                            metric = Series.of(query.metric),
                            context = QueryContext(
                                grouping = query.grouping,
                                filter = AnalyticsInputRecordFilter(
                                    fundIds = query.filter.fundIds.toSet(),
                                    units = query.filter.units.toSet(),
                                ),
                            ),
                        )
                    },
                )
            )
            call.respond(HttpStatusCode.OK, report.toTO(request))
        }
    }
}

private fun MetricResolutionReport.toTO(request: MetricsReportRequestTO): MetricsReportTO = MetricsReportTO(
    granularity = request.interval.granularity,
    buckets = buckets,
    series = request.queries.map { query ->
        val scalarSeries = series.getValue(QueryId(query.id))
        MetricSeriesTO(
            queryId = query.id,
            metric = query.metric,
            unit = query.metric.unit,
            currency = request.targetCurrency.takeIf { query.metric.unit == MetricUnitTypeTO.CURRENCY },
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
