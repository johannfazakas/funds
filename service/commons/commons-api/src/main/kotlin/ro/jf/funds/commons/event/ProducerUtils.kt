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

inline fun <reified T> createEventProducer(producerProperties: ProducerProperties, topic: Topic): EventProducer<T> =
    EventProducer(producerProperties, topic, T::class.java)

inline fun <reified T> createResponseProducer(
    producerProperties: ProducerProperties,
    topic: Topic
): ResponseProducer<T> =
    ResponseProducer(producerProperties, topic, T::class.java)

inline fun <reified T> createRequestProducer(producerProperties: ProducerProperties, topic: Topic): RequestProducer<T> =
    RequestProducer(producerProperties, topic, T::class.java)

class EventProducer<T>(
    producerProperties: ProducerProperties, topic: Topic, clazz: Class<T>
) : GenericProducer<T>(producerProperties, topic, clazz) {
    suspend fun send(userId: UUID, event: T) {
        super.send(userId.toString(), event, mapOf(USER_ID_HEADER to userId.toString()))
    }
}

class RequestProducer<T>(
    producerProperties: ProducerProperties, topic: Topic, clazz: Class<T>
) : GenericProducer<T>(producerProperties, topic, clazz) {
    suspend fun send(userId: UUID, correlationId: UUID, request: T) {
        val headers = mapOf(
            USER_ID_HEADER to userId.toString(),
            CORRELATION_ID_HEADER to correlationId.toString()
        )
        super.send(userId.toString(), request, headers)
    }
}

class ResponseProducer<T>(
    producerProperties: ProducerProperties, topic: Topic, clazz: Class<T>
) : GenericProducer<T>(producerProperties, topic, clazz) {
    suspend fun send(userId: UUID, correlationId: UUID, response: T) {
        val headers = mapOf(
            USER_ID_HEADER to userId.toString(),
            CORRELATION_ID_HEADER to correlationId.toString()
        )
        super.send(userId.toString(), response, headers)
    }
}

open class GenericProducer<T>(
    producerProperties: ProducerProperties,
    private val topic: Topic,
    clazz: Class<T>
) {
    protected val kafkaProducer = createProducer(producerProperties)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val serializable = serializer(clazz) as KSerializer<T>

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun send(key: String, payload: T, headers: Map<String, String> = emptyMap()) {
        val value = json.encodeToString(serializable, payload)
        val producerRecord = ProducerRecord(topic.value, key, value)
        headers.forEach { (k, v) -> producerRecord.headers().add(k, v.toByteArray()) }
        coroutineScope
            .launch { kafkaProducer.send(producerRecord).get() }
            .join()
    }
}

fun createProducer(properties: ProducerProperties): KafkaProducer<String, String> {
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
