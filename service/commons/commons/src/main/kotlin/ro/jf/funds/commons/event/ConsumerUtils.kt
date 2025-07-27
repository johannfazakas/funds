package ro.jf.funds.commons.event

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import ro.jf.funds.commons.observability.tracing.kafka.handleWithTracing
import java.time.Duration
import java.util.*

interface EventHandler<T> {
    suspend fun handle(event: Event<T>)
}

inline fun <reified T> createConsumer(
    properties: ConsumerProperties,
    topic: Topic,
    handler: EventHandler<T>,
): Consumer<T> =
    Consumer(properties, topic, handler) { it.asEvent<T>() }

open class Consumer<T>(
    properties: ConsumerProperties,
    private val topic: Topic,
    private val handler: EventHandler<T>,
    private val mapper: (ConsumerRecord<String, String>) -> Event<T>,
) : Closeable {
    private val consumer = createKafkaConsumer(properties)
    private lateinit var consumerJob: Job

    fun consume() {
        consumer.subscribe(listOf(topic.value))
        consumerJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val records = consumer.poll(Duration.ofMillis(100))
                records.forEach { record ->
                    record.handleWithTracing { handler.handle(mapper.invoke(this)) }
                }
            }
        }
    }

    override fun close() {
        consumerJob.cancel()
        consumer.close()
    }
}

fun createKafkaConsumer(properties: ConsumerProperties): KafkaConsumer<String, String> {
    return KafkaConsumer(Properties().also {
        it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = properties.bootstrapServers
        it[ConsumerConfig.GROUP_ID_CONFIG] = properties.groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        it[ConsumerConfig.FETCH_MAX_BYTES_CONFIG] = 20971520 // 20 MB
    })
}
