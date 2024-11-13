package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.commons.event.RequestConsumer
import ro.jf.funds.commons.event.ResponseConsumer
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO

private val logger = logger {}

fun Application.configureFundEventHandling() {

    val fundTransactionsBatchCreateRequestConsumer by inject<RequestConsumer<CreateFundTransactionsTO>>()
    val accountTransactionsBatchCreateResponseConsumer by inject<ResponseConsumer<GenericResponse>>(
        CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_CONSUMER
    )

    logger.info { "Configuring account event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        fundTransactionsBatchCreateRequestConsumer.consume()
        accountTransactionsBatchCreateResponseConsumer.consume()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        fundTransactionsBatchCreateRequestConsumer.close()
        accountTransactionsBatchCreateResponseConsumer.close()
    }
}
