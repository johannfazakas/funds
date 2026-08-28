package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.CreateDashboardChartTO
import ro.jf.funds.analytics.api.model.CreateDashboardTO
import ro.jf.funds.analytics.api.model.DashboardChartTO
import ro.jf.funds.analytics.api.model.DashboardLookbackTO
import ro.jf.funds.analytics.api.model.DashboardTO
import ro.jf.funds.analytics.api.model.UpdateDashboardChartPositionsTO
import ro.jf.funds.analytics.api.model.UpdateDashboardChartTO
import ro.jf.funds.analytics.api.model.UpdateDashboardPositionsTO
import ro.jf.funds.analytics.api.model.UpdateDashboardTO
import ro.jf.funds.analytics.service.domain.CreateDashboard
import ro.jf.funds.analytics.service.domain.CreateDashboardChart
import ro.jf.funds.analytics.service.domain.Dashboard
import ro.jf.funds.analytics.service.domain.DashboardChart
import ro.jf.funds.analytics.service.domain.DashboardLookback
import ro.jf.funds.analytics.service.domain.UpdateDashboard
import ro.jf.funds.analytics.service.domain.UpdateDashboardChart
import ro.jf.funds.analytics.service.service.DashboardService
import ro.jf.funds.platform.jvm.web.userId

private val log = logger { }

fun Routing.dashboardApiRouting(
    dashboardService: DashboardService,
) {
    route("/funds-api/analytics/v1/dashboards") {
        get {
            val userId = Uuid.fromString(call.userId().toString())
            call.respond(HttpStatusCode.OK, dashboardService.listDashboards(userId).map { it.toTO() })
        }
        post {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<CreateDashboardTO>()
            log.info { "Create dashboard request for user $userId: $request" }
            call.respond(HttpStatusCode.Created, dashboardService.createDashboard(userId, request.toDomain()).toTO())
        }
        put("/positions") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<UpdateDashboardPositionsTO>()
            log.info { "Reorder dashboards request for user $userId: $request" }
            call.respond(
                HttpStatusCode.OK,
                dashboardService.reorderDashboards(userId, request.dashboardIds).map { it.toTO() },
            )
        }
        route("/{dashboardId}") {
            get {
                val userId = Uuid.fromString(call.userId().toString())
                call.respond(HttpStatusCode.OK, dashboardService.getDashboard(userId, call.dashboardId()).toTO())
            }
            put {
                val userId = Uuid.fromString(call.userId().toString())
                val request = call.receive<UpdateDashboardTO>()
                log.info { "Update dashboard request for user $userId: $request" }
                call.respond(
                    HttpStatusCode.OK,
                    dashboardService.updateDashboard(userId, call.dashboardId(), request.toDomain()).toTO(),
                )
            }
            delete {
                val userId = Uuid.fromString(call.userId().toString())
                dashboardService.deleteDashboard(userId, call.dashboardId())
                call.respond(HttpStatusCode.NoContent)
            }
            route("/charts") {
                post {
                    val userId = Uuid.fromString(call.userId().toString())
                    val request = call.receive<CreateDashboardChartTO>()
                    log.info { "Append dashboard chart request for user $userId: $request" }
                    call.respond(
                        HttpStatusCode.Created,
                        dashboardService.appendChart(userId, call.dashboardId(), request.toDomain()).toTO(),
                    )
                }
                put("/positions") {
                    val userId = Uuid.fromString(call.userId().toString())
                    val request = call.receive<UpdateDashboardChartPositionsTO>()
                    log.info { "Reorder dashboard charts request for user $userId: $request" }
                    call.respond(
                        HttpStatusCode.OK,
                        dashboardService.reorderCharts(userId, call.dashboardId(), request.chartIds).map { it.toTO() },
                    )
                }
                route("/{chartId}") {
                    put {
                        val userId = Uuid.fromString(call.userId().toString())
                        val request = call.receive<UpdateDashboardChartTO>()
                        log.info { "Update dashboard chart request for user $userId: $request" }
                        call.respond(
                            HttpStatusCode.OK,
                            dashboardService
                                .updateChart(userId, call.dashboardId(), call.chartId(), request.toDomain())
                                .toTO(),
                        )
                    }
                    delete {
                        val userId = Uuid.fromString(call.userId().toString())
                        dashboardService.deleteChart(userId, call.dashboardId(), call.chartId())
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.dashboardId(): Uuid = uuidParameter("dashboardId")

private fun ApplicationCall.chartId(): Uuid = uuidParameter("chartId")

private fun ApplicationCall.uuidParameter(name: String): Uuid {
    val raw = parameters[name] ?: throw BadRequestException("Parameter '$name' is missing")
    return runCatching { Uuid.fromString(raw) }.getOrElse { throw BadRequestException("Invalid $name '$raw'") }
}

private fun Dashboard.toTO(): DashboardTO = DashboardTO(
    id = id,
    name = name,
    position = position,
    defaultGranularity = defaultGranularity,
    defaultLookback = DashboardLookbackTO(amount = defaultLookback.amount, unit = defaultLookback.unit),
    defaultTargetCurrency = defaultTargetCurrency,
    charts = charts.map { it.toTO() },
)

private fun DashboardChart.toTO(): DashboardChartTO = DashboardChartTO(
    id = id,
    name = name,
    position = position,
    queries = queries,
)

private fun CreateDashboardTO.toDomain(): CreateDashboard = CreateDashboard(
    name = name,
    defaultGranularity = defaultGranularity,
    defaultLookback = DashboardLookback(amount = defaultLookback.amount, unit = defaultLookback.unit),
    defaultTargetCurrency = defaultTargetCurrency,
    charts = charts.map { it.toDomain() },
)

private fun UpdateDashboardTO.toDomain(): UpdateDashboard = UpdateDashboard(
    name = name,
    defaultGranularity = defaultGranularity,
    defaultLookback = DashboardLookback(amount = defaultLookback.amount, unit = defaultLookback.unit),
    defaultTargetCurrency = defaultTargetCurrency,
)

private fun CreateDashboardChartTO.toDomain(): CreateDashboardChart = CreateDashboardChart(
    id = id,
    name = name,
    queries = queries,
)

private fun UpdateDashboardChartTO.toDomain(): UpdateDashboardChart = UpdateDashboardChart(
    name = name,
    queries = queries,
)
