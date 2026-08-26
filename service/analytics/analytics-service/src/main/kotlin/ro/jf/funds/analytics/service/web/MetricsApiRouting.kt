package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.MetricInfoTO
import ro.jf.funds.analytics.api.model.MetricSeriesGroupTO
import ro.jf.funds.analytics.api.model.MetricSeriesTO
import ro.jf.funds.analytics.api.model.MetricTO
import ro.jf.funds.analytics.api.model.MetricUnitTypeTO
import ro.jf.funds.analytics.api.model.MetricsReportRequestTO
import ro.jf.funds.analytics.api.model.MetricsReportTO
import ro.jf.funds.analytics.api.model.MetricsStreamBucketsTO
import ro.jf.funds.analytics.api.model.MetricsStreamErrorTO
import ro.jf.funds.analytics.api.model.MetricsStreamValueTO
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

private val streamJson = Json

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
            val report = metricResolutionService.resolve(request.toResolutionRequest(userId))
            call.respond(HttpStatusCode.OK, report.toTO(request))
        }
        post("/stream") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<MetricsReportRequestTO>()
            log.info { "Metrics stream request for user $userId: $request" }
            val resolutionRequest = request.toResolutionRequest(userId)
            // request receiving and validation happen above so invalid requests still get plain 400s;
            // once the SSE content starts the status is committed and failures can only be error events
            call.respond(SSEServerContent(call) {
                streamMetrics(metricResolutionService, request, resolutionRequest)
            })
        }
    }
}

private suspend fun ServerSSESession.streamMetrics(
    metricResolutionService: MetricResolutionService,
    request: MetricsReportRequestTO,
    resolutionRequest: MetricResolutionRequest,
) {
    try {
        sendEvent(
            "buckets",
            MetricsStreamBucketsTO(
                granularity = request.interval.granularity,
                buckets = resolutionRequest.interval.generateBuckets(),
            ),
        )
        metricResolutionService.resolveFlow(resolutionRequest).collect { bucketValue ->
            sendEvent(
                "value",
                MetricsStreamValueTO(
                    queryId = bucketValue.queryId.value,
                    bucket = bucketValue.bucket,
                    values = bucketValue.values.mapKeys { (groupKey, _) -> groupKey.apiValue },
                ),
            )
        }
        send(ServerSentEvent(data = "{}", event = "complete"))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        log.error(exception) { "Metrics stream resolution failed" }
        sendEvent("error", MetricsStreamErrorTO(exception.message ?: "Metric resolution failed"))
    }
}

private suspend inline fun <reified T> ServerSSESession.sendEvent(event: String, payload: T) {
    send(ServerSentEvent(data = streamJson.encodeToString(payload), event = event))
}

private fun MetricsReportRequestTO.toResolutionRequest(userId: Uuid): MetricResolutionRequest =
    MetricResolutionRequest(
        userId = userId,
        interval = ReportInterval(interval.granularity, interval.from, interval.to),
        targetCurrency = targetCurrency,
        queries = queries.map { query ->
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
