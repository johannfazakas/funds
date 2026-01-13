package ro.jf.funds.platform.jvm.observability.tracing.kafka

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future

private const val INSTRUMENTATION_SCOPE_NAME = "ro.jf.funds.kafka.producer"
private const val INSTRUMENTATION_SCOPE_VERSION = "1.0.0"

private val KAFKA_TOPIC_ATTRIBUTE_KEY = AttributeKey.stringKey("kafka.topic")
private val KAFKA_PARTITION_ATTRIBUTE_KEY = AttributeKey.longKey("kafka.partition")

object KafkaTextMapSetter : TextMapSetter<ProducerRecord<*, *>> {
    override fun set(
        carrier: ProducerRecord<*, *>?,
        key: String,
        value: String,
    ) {
        carrier?.headers()?.add(RecordHeader(key, value.toByteArray(StandardCharsets.UTF_8)))
    }
}

fun <K, V> KafkaProducer<K, V>.sendWithTracing(
    record: ProducerRecord<K, V>,
): Future<RecordMetadata> {
    val tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
    val propagator = GlobalOpenTelemetry.getPropagators().textMapPropagator
    val span = tracer.spanBuilder("produce ${record.topic()}")
        .setSpanKind(SpanKind.PRODUCER)
        .setParent(Context.current())
        .startSpan()
    val scope = span.makeCurrent()
    propagator.inject(Context.current(), record, KafkaTextMapSetter)

    try {
        return send(record) { meta, ex ->
            if (ex != null) {
                span.recordException(ex)
                span.setStatus(StatusCode.ERROR)
            } else {
                span.setAttribute(KAFKA_TOPIC_ATTRIBUTE_KEY, meta.topic())
                span.setAttribute(KAFKA_PARTITION_ATTRIBUTE_KEY, meta.partition().toLong())
                span.setStatus(StatusCode.OK)
            }
        }
    } catch (t: Throwable) {
        span.recordException(t)
        span.setStatus(StatusCode.ERROR)
        throw t
    } finally {
        span.end()
        scope.close()
    }
}