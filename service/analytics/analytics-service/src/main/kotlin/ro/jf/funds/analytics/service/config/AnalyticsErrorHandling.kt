package ro.jf.funds.analytics.service.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.service.domain.DashboardChartNotFoundException
import ro.jf.funds.analytics.service.domain.DashboardNotFoundException
import ro.jf.funds.analytics.service.domain.DashboardReorderException
import ro.jf.funds.platform.jvm.error.ErrorTO

private val logger = logger { }

fun Application.configureAnalyticsErrorHandling() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            logger.warn(cause) { "Bad request on ${call.request.httpMethod} ${call.request.path()}" }
            val detail = generateSequence(cause as Throwable) { it.cause }.last().message
            call.respond(HttpStatusCode.BadRequest, ErrorTO("Bad request", detail))
        }
        exception<DashboardReorderException> { call, cause ->
            logger.warn { "Invalid dashboard reorder on ${call.request.httpMethod} ${call.request.path()}: ${cause.message}" }
            call.respond(HttpStatusCode.BadRequest, ErrorTO("Bad request", cause.message))
        }
        exception<DashboardNotFoundException> { call, cause ->
            logger.warn { "Dashboard not found on ${call.request.httpMethod} ${call.request.path()}" }
            call.respond(HttpStatusCode.NotFound, ErrorTO("Not found", cause.message))
        }
        exception<DashboardChartNotFoundException> { call, cause ->
            logger.warn { "Dashboard chart not found on ${call.request.httpMethod} ${call.request.path()}" }
            call.respond(HttpStatusCode.NotFound, ErrorTO("Not found", cause.message))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unexpected error on ${call.request.httpMethod} ${call.request.path()}" }
            call.respond(HttpStatusCode.InternalServerError, ErrorTO.internal(cause))
        }
    }
}
