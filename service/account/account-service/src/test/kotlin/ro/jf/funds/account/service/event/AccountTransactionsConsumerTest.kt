package ro.jf.funds.account.service.event

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.module
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit


@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class AccountTransactionsConsumerTest {
    private val userId = randomUUID()
    private val accountId = randomUUID()
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
        // TODO(Johann) not everytime is working, something is hanging in there

        // TODO(Johann) should extract this producer to commons
        val producer = KafkaProducer<String, String>(Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaContainerExtension.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "test-producer")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000)
        })

        val consumer = KafkaConsumer<String, String>(Properties().apply {
            put("bootstrap.servers", KafkaContainerExtension.bootstrapServers)
            put("group.id", "test-consumer")
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("auto.offset.reset", "earliest")
        })
        // TODO(Johann) might rename to batch request or something. maybe account.transaction.batch-request
        // TODO(Johann) add some correlation id
        consumer.subscribe(listOf("local.funds.account.transactions-response"))

        producer.send(
            ProducerRecord(
                "local.funds.account.transactions-request",
                userId.toString(),
                createAccountTransactionsTO.toString()
            )
        ).get()

        await().atMost(30, TimeUnit.SECONDS).untilAsserted({
            val records = consumer.poll(Duration.ofSeconds(1))
            val response = records.firstOrNull { it.key() == userId.toString() }
            assertThat(response).isNotNull
            assertThat(response?.value()).isEqualTo("OK")
        })
    }
}
