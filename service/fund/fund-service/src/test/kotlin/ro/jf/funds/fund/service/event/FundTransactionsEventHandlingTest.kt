package ro.jf.funds.fund.service.event

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.api.model.propertiesOf
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.test.utils.testTopicSupplier
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.module
import ro.jf.funds.fund.service.service.METADATA_FUND_ID
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class FundTransactionsEventHandlingTest {
    private val createFundTransactionsRequestTopic =
        testTopicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST)
    private val createAccountTransactionsRequestTopic =
        testTopicSupplier.topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST)
    private val createAccountTransactionsResponseTopic =
        testTopicSupplier.topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE)
    private val createFundTransactionsResponseTopic =
        testTopicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE)

    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")
    private val producerProperties = ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-client")

    private val userId = randomUUID()
    private val correlationId = randomUUID()
    private val fundId = randomUUID()
    private val accountId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-01-01T00:00:00")
    private val createFundTransactionsTO = CreateFundTransactionsTO(
        transactions = listOf(
            CreateFundTransactionTO(
                dateTime = dateTime,
                records = listOf(
                    CreateFundRecordTO(
                        fundId = fundId,
                        accountId = accountId,
                        amount = BigDecimal("100.0"),
                        unit = Currency.RON
                    )
                )
            )
        )
    )

    @Test
    fun `test consume fund transactions request`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, appConfig)
        startApplication()

        val createAccountTransactionsConsumer = createKafkaConsumer(consumerProperties)
        createAccountTransactionsConsumer.subscribe(listOf(createAccountTransactionsRequestTopic.value))

        val producer =
            createProducer<CreateFundTransactionsTO>(producerProperties, createFundTransactionsRequestTopic)

        producer.send(Event(userId, createFundTransactionsTO, correlationId, userId.toString()))

        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val createAccountTransactionsRequest =
                createAccountTransactionsConsumer.poll(Duration.ofSeconds(1)).toList()
                    .map { it.asEvent<CreateAccountTransactionsTO>() }
                    .firstOrNull { it.correlationId == correlationId }
            assertThat(createAccountTransactionsRequest).isNotNull
            assertThat(createAccountTransactionsRequest!!.userId).isEqualTo(userId)
            assertThat(createAccountTransactionsRequest.payload).isEqualTo(
                CreateAccountTransactionsTO(
                    transactions = listOf(
                        CreateAccountTransactionTO(
                            dateTime = dateTime,
                            records = listOf(
                                CreateAccountRecordTO(
                                    accountId = accountId,
                                    amount = BigDecimal("100.0"),
                                    unit = Currency.RON,
                                    properties = propertiesOf(
                                        METADATA_FUND_ID to fundId.toString()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `test consumer account transactions response`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, appConfig)
        startApplication()

        val createFundTransactionsResponseConsumer = createKafkaConsumer(consumerProperties)
        createFundTransactionsResponseConsumer.subscribe(listOf(createFundTransactionsResponseTopic.value))

        val createAccountTransactionsResponseProducer =
            createProducer<GenericResponse>(producerProperties, createAccountTransactionsResponseTopic)

        createAccountTransactionsResponseProducer.send(
            Event(
                userId,
                GenericResponse.Success,
                correlationId,
                userId.toString()
            )
        )

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val createFundTransactionsResponse =
                createFundTransactionsResponseConsumer.poll(Duration.ofSeconds(1)).toList()
                    .map { it.asEvent<GenericResponse>() }
                    .firstOrNull { it.correlationId == correlationId }
            assertThat(createFundTransactionsResponse).isNotNull
            assertThat(createFundTransactionsResponse!!.userId).isEqualTo(userId)
            assertThat(createFundTransactionsResponse.correlationId).isEqualTo(correlationId)
            assertThat(createFundTransactionsResponse.payload).isEqualTo(GenericResponse.Success)
        }
    }

    private val appConfig = MapApplicationConfig(
        "integration.account-service.base-url" to MockServerContainerExtension.baseUrl
    )
}