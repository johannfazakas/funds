package ro.jf.funds.commons.event

import io.ktor.server.application.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import ro.jf.funds.commons.config.getStringProperty
import java.time.Duration
import java.util.*

private const val KAFKA_BOOTSTRAP_SERVERS_PROPERTY = "kafka.bootstrapServers"
private const val KAFKA_GROUP_ID_PROPERTY = "kafka.groupId"

inline fun <reified T> createEventConsumer(
    properties: ConsumerProperties, topic: Topic, noinline handler: suspend (Event<T>) -> Unit
): Consumer {
    return Consumer(properties, topic, { handler(it.asEvent()) })
}

inline fun <reified T> createRequestConsumer(
    properties: ConsumerProperties, topic: Topic, noinline handler: suspend (RpcRequest<T>) -> Unit
): Consumer {
    return Consumer(properties, topic, { handler(it.asRequest()) })
}

inline fun <reified T> createResponseConsumer(
    properties: ConsumerProperties, topic: Topic, noinline handler: suspend (RpcResponse<T>) -> Unit
): Consumer {
    return Consumer(properties, topic, { handler(it.asResponse()) })
}

// TODO(Johann) if handlers could be attached separately, maybe Consumers could be injected
class Consumer(
    properties: ConsumerProperties,
    private val topic: Topic,
    private val handler: suspend (ConsumerRecord<String, String>) -> Unit
) : Closeable {
    private val consumer = createKafkaConsumer(properties)
    private lateinit var consumerJob: Job

    fun consume() {
        consumer.subscribe(listOf(topic.value))
        consumerJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val records = consumer.poll(Duration.ofMillis(500))
                records.forEach {
                    handler.invoke(it)
                }
            }
        }
    }

    override fun close() {
        consumerJob.cancel()
        consumer.close()
    }
}

inline fun <reified T> ConsumerRecord<String, String>.asEvent(): Event<T> =
    Event(userId(), key(), payload())

inline fun <reified T> ConsumerRecord<String, String>.asRequest(): RpcRequest<T> =
    RpcRequest(userId(), correlationId(), key(), payload())

inline fun <reified T> ConsumerRecord<String, String>.asResponse(): RpcResponse<T> =
    RpcResponse(userId(), correlationId(), key(), payload())

inline fun <reified T> ConsumerRecord<String, String>.payload(): T = Json.decodeFromString(serializer<T>(), value())

fun ConsumerRecord<String, String>.userId(): UUID = UUID.fromString(header(USER_ID_HEADER))

fun ConsumerRecord<String, String>.correlationId(): UUID = UUID.fromString(header(CORRELATION_ID_HEADER))

fun ConsumerRecord<String, String>.header(key: String): String = headers().lastHeader(key).value().let(::String)

// TODO(Johann) could add another layer to do things like createEventConsumer which would poll Event records? or it might be available only for test purposes
// TODO(Johann) should this be private?
fun createKafkaConsumer(properties: ConsumerProperties): KafkaConsumer<String, String> {
    return KafkaConsumer(Properties().also {
        it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = properties.bootstrapServers
        it[ConsumerConfig.GROUP_ID_CONFIG] = properties.groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    })
}

data class ConsumerProperties(
    val bootstrapServers: String,
    val groupId: String
) {
    companion object {
        fun fromEnv(config: ApplicationEnvironment): ConsumerProperties {
            return ConsumerProperties(
                bootstrapServers = config.getStringProperty(KAFKA_BOOTSTRAP_SERVERS_PROPERTY),
                groupId = config.getStringProperty(KAFKA_GROUP_ID_PROPERTY)
            )
        }
    }
}

