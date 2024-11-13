package ro.jf.funds.commons.event

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.*

data class Event<T>(
    val userId: UUID,
    val payload: T,
    val correlationId: UUID? = null,
    val key: String = userId.toString()
)

inline fun <reified T> ConsumerRecord<String, String>.asEvent(): Event<T> =
    Event(userId = userId(), payload = payload(), correlationId = correlationId(), key = key())

inline fun <reified T> ConsumerRecord<String, String>.payload(): T =
    Json.decodeFromString(serializer<T>(), value())

fun ConsumerRecord<String, String>.userId(): UUID =
    UUID.fromString(header(USER_ID_HEADER) ?: error { "Missing user id" })

fun ConsumerRecord<String, String>.correlationId(): UUID = UUID.fromString(header(CORRELATION_ID_HEADER))

fun ConsumerRecord<String, String>.header(key: String): String? = headers().lastHeader(key)?.value()?.let {
    io.ktor.utils.io.core.String(
        it
    )
}
