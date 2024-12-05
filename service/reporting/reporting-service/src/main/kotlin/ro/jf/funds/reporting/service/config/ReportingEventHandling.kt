package ro.jf.funds.reporting.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.commons.event.Consumer
import ro.jf.funds.reporting.api.model.CreateReportViewTO

private val logger = logger { }

fun Application.configureReportingEventHandling() {

    val createReportViewTaskConsumer by inject<Consumer<CreateReportViewTO>>()

    logger.info { "Configuring reporting event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        createReportViewTaskConsumer.consume()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        createReportViewTaskConsumer.close()
    }
}
