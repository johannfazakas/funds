package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import mu.KotlinLogging.logger
import org.koin.ktor.ext.inject
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.service.FundTransactionService

private val logger = logger {}

fun Application.configureFundEventHandling() {
    val topicSupplier by inject<TopicSupplier>()
    val consumerProperties by inject<ConsumerProperties>()
    val producerProperties by inject<ProducerProperties>()
    val fundTransactionsService by inject<FundTransactionService>()

    val fundTransactionsRequestTopic = topicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST)
    val fundTransactionsResponseTopic = topicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE)
    val accountTransactionsResponseTopic = topicSupplier.topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE)

    val fundTransactionsBatchCreateRequestConsumer =
        createRequestConsumer<CreateFundTransactionsTO>(consumerProperties, fundTransactionsRequestTopic) {
            logger.info { "Received create fund transactions request $it" }
            fundTransactionsService.createTransactions(it.userId, it.correlationId, it.payload)
        }

    val fundTransactionsBatchCreateResponseProducer =
        createResponseProducer<GenericResponse>(producerProperties, fundTransactionsResponseTopic)

    val accountTransactionsBatchCreateResponseConsumer = createResponseConsumer<GenericResponse>(
        consumerProperties,
        accountTransactionsResponseTopic
    ) {
        logger.info { "Received create fund transactions response $it" }
        fundTransactionsBatchCreateResponseProducer.send(it.userId, it.correlationId, it.payload)
    }

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
