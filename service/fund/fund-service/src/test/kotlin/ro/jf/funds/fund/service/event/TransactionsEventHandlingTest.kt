package ro.jf.funds.fund.service.event

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
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
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.module
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class TransactionsEventHandlingTest {
    private val fundRepository = createFundRepository()
    private val accountRepository = createAccountRepository()
    private val accountTransactionRepository = createAccountTransactionRepository()

    private val createFundTransactionsRequestTopic =
        testTopicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST)
    private val createFundTransactionsResponseTopic =
        testTopicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE)

    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")
    private val producerProperties = ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-client")

    private val userId = randomUUID()
    private val correlationId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-01-01T00:00:00")

    @Test
    fun `test consume fund transactions request`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, appConfig)
        startApplication()

        val fund = fundRepository.save(userId, CreateFundTO(FundName("Expenses")))
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("Revolut"), Currency.RON))

        val createFundTransactionsResponseConsumer = createKafkaConsumer(consumerProperties)
        createFundTransactionsResponseConsumer.subscribe(listOf(createFundTransactionsResponseTopic.value))

        val producer =
            createProducer<CreateTransactionsTO>(producerProperties, createFundTransactionsRequestTopic)

        val externalId = randomUUID().toString()
        val createFundTransactionsTO = CreateTransactionsTO(
            transactions = listOf(
                CreateTransactionTO(
                    dateTime = dateTime,
                    externalId = externalId,
                    type = TransactionType.SINGLE_RECORD,
                    records = listOf(
                        CreateTransactionRecord(
                            fundId = fund.id,
                            accountId = account.id,
                            amount = BigDecimal("100.0"),
                            unit = Currency.RON
                        )
                    )
                )
            )
        )

        producer.send(Event(userId, createFundTransactionsTO, correlationId, userId.toString()))

        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val createFundTransactionsResponse =
                createFundTransactionsResponseConsumer.poll(Duration.ofSeconds(1)).toList()
                    .map { it.asEvent<GenericResponse>() }
                    .firstOrNull { it.correlationId == correlationId }
            assertThat(createFundTransactionsResponse).isNotNull
            assertThat(createFundTransactionsResponse!!.userId).isEqualTo(userId)
            assertThat(createFundTransactionsResponse.correlationId).isEqualTo(correlationId)
            assertThat(createFundTransactionsResponse.payload).isEqualTo(GenericResponse.Success)
        }

        // Verify the transaction was actually persisted to the repository
        val persistedTransactions = accountTransactionRepository.list(userId, TransactionFilterTO.empty())
        assertThat(persistedTransactions).hasSize(1)
        assertThat(persistedTransactions[0].externalId).isEqualTo(externalId)
        assertThat(persistedTransactions[0].records).hasSize(1)
        assertThat(persistedTransactions[0].records[0].accountId).isEqualTo(account.id)
        assertThat(persistedTransactions[0].records[0].amount).isEqualByComparingTo(BigDecimal("100.0"))
        assertThat(persistedTransactions[0].records[0].unit).isEqualTo(Currency.RON)
    }

    private val appConfig = MapApplicationConfig(
        "integration.account-service.base-url" to MockServerContainerExtension.baseUrl
    )

    private fun createFundRepository() = FundRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )

    private fun createAccountRepository() = AccountRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )

    private fun createAccountTransactionRepository() = TransactionRepository(
        database = Database.connect(
            url = PostgresContainerExtension.jdbcUrl,
            user = PostgresContainerExtension.username,
            password = PostgresContainerExtension.password
        )
    )
}