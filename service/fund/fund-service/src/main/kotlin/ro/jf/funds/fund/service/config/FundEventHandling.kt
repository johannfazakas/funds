package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.commons.event.Consumer
import ro.jf.funds.fund.api.model.CreateTransactionsTO

private val logger = logger {}

fun Application.configureFundEventHandling() {

    val fundTransactionsBatchCreateRequestConsumer by inject<Consumer<CreateTransactionsTO>>()

    logger.info { "Configuring fund event handling" }
    monitor.subscribe(ApplicationStarted) {
        fundTransactionsBatchCreateRequestConsumer.consume()
    }

    monitor.subscribe(ApplicationStopped) {
        fundTransactionsBatchCreateRequestConsumer.close()
    }
}
