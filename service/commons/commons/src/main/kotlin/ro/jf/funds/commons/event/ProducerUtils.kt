package ro.jf.funds.commons.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.clients.producer.ProducerRecord

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
