package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse

private val logger = logger {}

fun Application.configureAccountEventHandling() {
    // TODO(Johann) should this be injected? after all this is a configuration
    val consumerProperties by inject<ConsumerProperties>()
    val producerProperties by inject<ProducerProperties>()
    val accountTransactionService by inject<AccountTransactionService>()

    val transactionsBatchCreateResponseProducer = createResponseProducer<GenericResponse>(
        producerProperties,
        Topic("local.funds.account.transactions-response")
    )

    val transactionsBatchCreateRequestConsumer = createRequestConsumer<CreateAccountTransactionsTO>(
        consumerProperties,
        Topic("local.funds.account.transactions-request")
    ) {
        logger.info { "Received create account transactions request $it" }
        accountTransactionService.createTransactions(it.userId, it.payload)
        transactionsBatchCreateResponseProducer.send(it.userId, it.correlationId, GenericResponse.Success)
    }

    logger.info { "Configuring account event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        transactionsBatchCreateRequestConsumer.consume()
//
//        consumeRequests<CreateAccountTransactionsTO>(
//            consumerProperties,
//            Topic("local.funds.account.transactions-request")
//        ) { request ->
//            logger.info { "Received create account transactions request $request" }
//            accountTransactionService.createTransactions(request.userId, request.payload)
//            transactionsBatchCreateResponseProducer.send(request.userId, request.correlationId, GenericResponse.Success)
//        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        transactionsBatchCreateRequestConsumer.close()
    }
}
