package ro.jf.funds.reporting.service.utils

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext

// TODO(Johann) should extract everything tracing related to commons

fun <T> withSpan(spanName: String, vararg attributes: Pair<String, Any?>, block: () -> T): T {
    // TODO(Johann-19) but everything is "Lambda" now as a method. maybe it should be passed for non suspending methods
    val (klass) = block.javaClass.name.split('$')
    val span = buildSpan(klass, spanName, attributes.toList())
    return span.makeCurrent().use {
        try {
            block()
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR)
            throw t
        } finally {
            span.end()
        }
    }
}

suspend fun <T> withSuspendingSpan(vararg attributes: Pair<String, Any?>, block: suspend () -> T): T {
    val klass = block.javaClass.enclosingClass.name
    val method = block.javaClass.enclosingMethod.name
    val span = buildSpan(klass, method, attributes.toList())
    return span.makeCurrent().use {
        try {
            withContext(span.asContextElement()) {
                block()
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

private fun buildSpan(
    scopeName: String,
    spanName: String,
    attributes: List<Pair<String, Any?>>,
): Span = GlobalOpenTelemetry.getTracer(scopeName)
    .spanBuilder(spanName)
    .setSpanKind(SpanKind.INTERNAL)
    .apply { attributes.forEach { setAttribute(it) } }
    .startSpan()

private fun SpanBuilder.setAttribute(attribute: Pair<String, Any?>): SpanBuilder {
    val (key, value) = attribute
    return when (value) {
        is Long -> setAttribute(key, value)
        is Int -> setAttribute(key, value.toLong())
        is Number -> setAttribute(key, value.toDouble())
        is Boolean -> setAttribute(key, value)
        else -> setAttribute(key, value.toString())
    }
}
