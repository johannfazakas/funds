package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.CREATE_ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.event.CREATE_ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse

private val logger = logger {}

fun Application.configureAccountEventHandling() {
    val topicSupplier by inject<TopicSupplier>()
    // TODO(Johann) should this be injected? after all this is a configuration
    val consumerProperties by inject<ConsumerProperties>()
    val producerProperties by inject<ProducerProperties>()
    val accountTransactionService by inject<AccountTransactionService>()

    val transactionsBatchCreateResponseProducer = createResponseProducer<GenericResponse>(
        producerProperties,
        topicSupplier.getTopic(ACCOUNT_DOMAIN, CREATE_ACCOUNT_TRANSACTIONS_RESPONSE),
    )

    val transactionsBatchCreateRequestConsumer = createRequestConsumer<CreateAccountTransactionsTO>(
        consumerProperties,
        topicSupplier.getTopic(ACCOUNT_DOMAIN, CREATE_ACCOUNT_TRANSACTIONS_REQUEST)
    ) {
        logger.info { "Received create account transactions request $it" }
        accountTransactionService.createTransactions(it.userId, it.payload)
        transactionsBatchCreateResponseProducer.send(it.userId, it.correlationId, GenericResponse.Success)
    }

    logger.info { "Configuring account event handling" }
    environment.monitor.subscribe(ApplicationStarted) {
        transactionsBatchCreateRequestConsumer.consume()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        transactionsBatchCreateRequestConsumer.close()
    }
}
