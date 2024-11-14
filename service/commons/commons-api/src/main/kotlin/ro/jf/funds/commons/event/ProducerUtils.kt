package ro.jf.funds.commons.event

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import ro.jf.funds.commons.config.getStringProperty
import java.util.*

private const val KAFKA_BOOTSTRAP_SERVERS_PROPERTY = "kafka.bootstrapServers"
private const val KAFKA_CLIENT_ID_PROPERTY = "kafka.clientId"

inline fun <reified T> createProducer(properties: ProducerProperties, topic: Topic): Producer<T> =
    Producer(properties, topic, T::class.java)

open class Producer<T>(
    producerProperties: ProducerProperties,
    private val topic: Topic,
    clazz: Class<T>
) {
    private val kafkaProducer = createKafkaProducer(producerProperties)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val serializable = serializer(clazz) as KSerializer<T>

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun send(event: Event<T>) {
        val headers = mutableMapOf(USER_ID_HEADER to event.userId.toString())
        if (event.correlationId != null) {
            headers[CORRELATION_ID_HEADER] = event.correlationId.toString()
        }
        send(event.key, event.payload, headers)
    }

    private suspend fun send(key: String, payload: T, headers: Map<String, String> = emptyMap()) {
        val value = json.encodeToString(serializable, payload)
        val producerRecord = ProducerRecord(topic.value, key, value)
        headers.forEach { (k, v) -> producerRecord.headers().add(k, v.toByteArray()) }
        coroutineScope
            .launch { kafkaProducer.send(producerRecord).get() }
            .join()
    }
}

fun createKafkaProducer(properties: ProducerProperties): KafkaProducer<String, String> {
    return KafkaProducer(Properties().also {
        it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = properties.bootstrapServers
        it[ProducerConfig.CLIENT_ID_CONFIG] = properties.clientId
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.RETRIES_CONFIG] = 3
        it[ProducerConfig.ACKS_CONFIG] = "all"
    })
}

data class ProducerProperties(
    val bootstrapServers: String,
    val clientId: String
) {
    companion object {
        fun fromEnv(config: ApplicationEnvironment): ProducerProperties {
            return ProducerProperties(
                bootstrapServers = config.getStringProperty(KAFKA_BOOTSTRAP_SERVERS_PROPERTY),
                clientId = config.getStringProperty(KAFKA_CLIENT_ID_PROPERTY)
            )
        }
    }
}
