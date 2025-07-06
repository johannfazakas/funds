package ro.jf.funds.commons.observability.tracing.kafka

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.nio.charset.StandardCharsets

private const val INSTRUMENTATION_SCOPE_NAME = "ro.jf.funds.kafka.consumer"
private const val INSTRUMENTATION_SCOPE_VERSION = "1.0.0"

private val KAFKA_TOPIC_ATTRIBUTE_KEY = AttributeKey.stringKey("kafka.topic")
private val KAFKA_PARTITION_ATTRIBUTE_KEY = AttributeKey.longKey("kafka.partition")

object KafkaTextMapGetter : TextMapGetter<ConsumerRecord<*, *>> {
    override fun keys(carrier: ConsumerRecord<*, *>): Iterable<String> =
        carrier.headers().map { it.key() }

    override fun get(carrier: ConsumerRecord<*, *>?, key: String): String? =
        carrier?.headers()?.lastHeader(key)?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }
}

suspend fun <K, V> ConsumerRecord<K, V>.handleWithTracing(
    handler: suspend ConsumerRecord<K, V>.() -> Unit,
) {
    val tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
    val textMapPropagator = GlobalOpenTelemetry.getPropagators().textMapPropagator

    val parentContext = textMapPropagator
        .extract(Context.current(), this, KafkaTextMapGetter)
    val span = tracer.spanBuilder("${topic()}")
        .setParent(parentContext)
        .setSpanKind(SpanKind.CONSUMER)
        .setAttribute(KAFKA_TOPIC_ATTRIBUTE_KEY, topic())
        .setAttribute(KAFKA_PARTITION_ATTRIBUTE_KEY, partition().toLong())
        .startSpan()

    return span.makeCurrent().use {
        try {
            withContext(span.asContextElement()) {
                handler.invoke(this@handleWithTracing)
            }
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR)
            throw t
        } finally {
            span.end()
        }
    }
}
