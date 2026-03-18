package ro.jf.funds.analytics.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.fund.api.model.TransactionsCreatedTO
import ro.jf.funds.platform.jvm.event.Consumer

private val logger = logger {}

fun Application.configureAnalyticsEventHandling() {
    val transactionsCreatedConsumer by inject<Consumer<TransactionsCreatedTO>>()

    logger.info { "Configuring analytics event handling" }
    monitor.subscribe(ApplicationStarted) {
        transactionsCreatedConsumer.consume()
    }

    monitor.subscribe(ApplicationStopped) {
        transactionsCreatedConsumer.close()
    }
}
