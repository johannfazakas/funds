package ro.jf.funds.account.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

@ExtendWith(KafkaContainerExtension::class)
class AccountTransactionAsyncSdkTest {
    val accountTransactionAsyncSdk = AccountTransactionAsyncSdk(
        bootstrapServers = KafkaContainerExtension.bootstrapServers, clientId = "test-client"
    )

    private val userId = randomUUID()
    private val accountId = randomUUID()
    private val consumerGroupId = "consumer-group-${randomUUID()}"
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
    fun `given create transactions`(): Unit = runBlocking {
        val consumer = createTestConsumer()
        // TODO(Johann) the topic name or pattern should be extracted somewhere
        consumer.subscribe(listOf("local.funds.account.transactions-request"))

        accountTransactionAsyncSdk.createTransactions(userId, createAccountTransactionsTO)

        await().atMost(20, TimeUnit.SECONDS).untilAsserted({
            val records = consumer.poll(Duration.ofSeconds(1))
            assertThat(records).anyMatch { it.key() == userId.toString() }
            val record = records.first { it.key() == userId.toString() }
            assertThat(record.key()).isEqualTo(userId.toString())
            assertThat(Json.decodeFromString<CreateAccountTransactionsTO>(record.value()))
                .isEqualTo(createAccountTransactionsTO)
        })
    }

    private fun createTestConsumer(): KafkaConsumer<String, String> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaContainerExtension.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        }

        return KafkaConsumer(props, StringDeserializer(), StringDeserializer())
    }
}
