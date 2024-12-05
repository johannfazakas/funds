package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.commons.event.Consumer
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO

private val logger = logger {}

fun Application.configureFundEventHandling() {

    val fundTransactionsBatchCreateRequestConsumer by inject<Consumer<CreateFundTransactionsTO>>()
    val accountTransactionsBatchCreateResponseConsumer by inject<Consumer<GenericResponse>>(
        CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_CONSUMER
    )

    logger.info { "Configuring fund event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        fundTransactionsBatchCreateRequestConsumer.consume()
        accountTransactionsBatchCreateResponseConsumer.consume()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        fundTransactionsBatchCreateRequestConsumer.close()
        accountTransactionsBatchCreateResponseConsumer.close()
    }
}
