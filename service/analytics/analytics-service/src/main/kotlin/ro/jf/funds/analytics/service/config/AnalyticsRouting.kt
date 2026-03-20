package ro.jf.funds.analytics.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.analytics.service.service.AnalyticsRecordService
import ro.jf.funds.analytics.service.web.analyticsApiRouting

fun Application.configureAnalyticsRouting() {
    routing {
        analyticsApiRouting(get<AnalyticsRecordService>())
    }
}
