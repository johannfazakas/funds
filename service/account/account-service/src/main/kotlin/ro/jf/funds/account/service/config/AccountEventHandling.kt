package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.commons.event.Consumer

private val logger = logger {}

fun Application.configureAccountEventHandling() {
    val transactionsBatchCreateRequestConsumer by inject<Consumer<CreateAccountTransactionsTO>>()

    logger.info { "Configuring account event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        transactionsBatchCreateRequestConsumer.consume()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        transactionsBatchCreateRequestConsumer.close()
    }
}
