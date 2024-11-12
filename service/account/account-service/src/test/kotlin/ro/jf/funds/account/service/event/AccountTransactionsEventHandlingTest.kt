package ro.jf.funds.account.service.event

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.service.domain.Account
import ro.jf.funds.account.service.module
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.test.utils.testTopicSupplier
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountTransactionsEventHandlingTest {
    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)
    private val accountTransactionRepository = AccountTransactionRepository(PostgresContainerExtension.connection)

    private val createTransactionsRequestTopic =
        testTopicSupplier.topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST)
    private val createTransactionsResponseTopic =
        testTopicSupplier.topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE)

    private val userId = randomUUID()
    private val correlationId = randomUUID()

    @AfterEach
    fun tearDown() = runBlocking {
        accountTransactionRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `test consume account transactions request`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)
        startApplication()

        val account = account(AccountName("Cash"))
        val createAccountTransactionsTO = createAccountTransactionsTO(account, BigDecimal("100.0"))

        val consumer = createKafkaConsumer(ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer"))

        consumer.subscribe(listOf(createTransactionsResponseTopic.value))
        val producer = createRequestProducer<CreateAccountTransactionsTO>(
            ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-producer"),
            Topic(createTransactionsRequestTopic.value)
        )

        producer.send(userId, correlationId, createAccountTransactionsTO)

        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val records = consumer.poll(Duration.ofSeconds(1))
            val response = records.toList().map { it.asResponse<GenericResponse>() }
                .firstOrNull { it.correlationId == correlationId }
            assertThat(response).isNotNull
            assertThat(response!!.payload).isInstanceOf(GenericResponse.Success::class.java)
        }

        val transactions = accountTransactionRepository.list(userId)
        assertThat(transactions).hasSize(1)
        val transaction = transactions.first()
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(createAccountTransactionsTO.transactions.first().dateTime)
        assertThat(transaction.records).hasSize(1)
        val record = transaction.records.first()
        assertThat(record.amount).isEqualByComparingTo(BigDecimal("100.0"))
        assertThat(record.unit).isEqualTo(Currency.RON)
        assertThat(record.accountId).isEqualTo(account.id)
    }

    private suspend fun account(name: AccountName): Account =
        accountRepository.save(userId, CreateAccountTO(name, Currency.RON))

    private fun createAccountTransactionsTO(account: Account, amount: BigDecimal): CreateAccountTransactionsTO =
        CreateAccountTransactionsTO(
            transactions = listOf(
                CreateAccountTransactionTO(
                    dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
                    records = listOf(
                        CreateAccountRecordTO(
                            accountId = account.id,
                            amount = amount,
                            unit = Currency.RON,
                            emptyMap()
                        ),
                    )
                )
            )
        )
}
