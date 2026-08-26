package ro.jf.funds.analytics.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.analytics.service.service.MetricResolutionService
import ro.jf.funds.analytics.service.web.metricsApiRouting

fun Application.configureAnalyticsRouting() {
    routing {
        metricsApiRouting(get<MetricResolutionService>())
    }
}
