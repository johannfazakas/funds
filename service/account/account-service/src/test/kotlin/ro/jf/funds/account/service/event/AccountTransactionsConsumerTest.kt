package ro.jf.funds.account.service.event

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.module
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountTransactionsConsumerTest {
    private val userId = randomUUID()
    private val accountId = randomUUID()
    private val correlationId = randomUUID()
    private val createAccountTransactionsTO = CreateAccountTransactionsTO(
        transactions = listOf(
            CreateAccountTransactionTO(
                dateTime = LocalDateTime.parse("2021-09-01T12:00:00"),
                records = listOf(
                    CreateAccountRecordTO(
                        accountId = accountId,
                        amount = BigDecimal("100.0"),
                        unit = Currency.RON,
                        emptyMap()
                    ),
                )
            )
        )
    )

    @Test
    fun `test consume account transactions request`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig)
        startApplication()

        val consumer = createConsumer(ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer"))
        consumer.subscribe(listOf("local.funds.account.transactions-response"))

        val producer = createRequestProducer<CreateAccountTransactionsTO>(
            ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-producer"),
            Topic("local.funds.account.transactions-request")
        )
        producer.send(userId, correlationId, createAccountTransactionsTO)

        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val records = consumer.poll(Duration.ofSeconds(1))
            val response = records.toList().map { it.asResponse<GenericResponse>() }
                .firstOrNull { it.correlationId == correlationId }
            assertThat(response).isNotNull
            assertThat(response!!.payload).isInstanceOf(GenericResponse.Success::class.java)
        }
    }
}
