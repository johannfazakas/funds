package ro.jf.funds.fund.service.event

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.model.GenericResponse
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.test.utils.testTopicSupplier
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.module
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
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
        testTopicSupplier.topic(FundEvents.FundTransactionsBatchRequest)
    private val createFundTransactionsResponseTopic =
        testTopicSupplier.topic(FundEvents.FundTransactionsBatchResponse)

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
                CreateTransactionTO.SingleRecord(
                    dateTime = dateTime,
                    externalId = externalId,
                    record = CreateTransactionRecordTO(
                        fundId = fund.id,
                        accountId = account.id,
                        amount = BigDecimal.parseString("100.0"),
                        unit = Currency.RON
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

        val persistedTransactions = accountTransactionRepository.list(userId, TransactionFilterTO.empty())
        assertThat(persistedTransactions).hasSize(1)
        val transaction = persistedTransactions[0] as ro.jf.funds.fund.service.domain.Transaction.SingleRecord
        assertThat(transaction.externalId).isEqualTo(externalId)
        assertThat(transaction.record.accountId).isEqualTo(account.id)
        assertThat(transaction.record.amount).isEqualByComparingTo(BigDecimal.parseString("100.0"))
        assertThat(transaction.record.unit).isEqualTo(Currency.RON)
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