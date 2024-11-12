package ro.jf.funds.account.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import ro.jf.funds.account.api.AccountTransactionAsyncApi
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import java.util.*

// TODO(Johann) it could completely not be needed after all, generic request producer might be enough
class AccountTransactionAsyncSdk(
    // TODO(Johann) could be replaced by kafka producer
    private val bootstrapServers: String,
    private val clientId: String
) : AccountTransactionAsyncApi {
    // TODO(Johann) extract producer
    private val producer: KafkaProducer<String, String>

    init {
        producer = KafkaProducer(Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000)
        }, StringSerializer(), StringSerializer())
    }

    override suspend fun createTransactions(userId: UUID, request: CreateAccountTransactionsTO) {
        try {
            val topic = "local.funds.account.transactions-request"
            val key = userId.toString()
            val message = Json.encodeToString(request)
            val record = ProducerRecord(topic, key, message)
            // TODO(Johann) this might be extracted to some commons place
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
        } catch (e: Exception) {
            throw RuntimeException("Error publishing message to Kafka", e)
        }
    }
}
