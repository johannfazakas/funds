package ro.jf.funds.commons.observability.tracing.ktor

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes

private const val INSTRUMENTATION_SCOPE_NAME = "ro.jf.funds.ktor.client"
private const val INSTRUMENTATION_SCOPE_VERSION = "1.0.0"

private val SPAN_ATTRIBUTE_KEY = AttributeKey<Span>("otel-span")
private val SCOPE_ATTRIBUTE_KEY = AttributeKey<Scope>("otel-scope")

private object KtorClientTextMapSetter : TextMapSetter<HttpRequestBuilder> {
    override fun set(carrier: HttpRequestBuilder?, key: String, value: String) {
        carrier?.headers?.append(key, value)
    }
}

val KtorClientTracing = createClientPlugin("FundsKtorClientTracing") {
    val openTelemetrySet = GlobalOpenTelemetry.get() != OpenTelemetry.noop()
    val tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
    val propagator = GlobalOpenTelemetry.getPropagators().textMapPropagator

    onRequest { request, _ ->
        if (!openTelemetrySet) return@onRequest

        val span = tracer.spanBuilder("${request.method.value}:${request.url.encodedPath}")
            .setSpanKind(SpanKind.CLIENT)
            .setParent(Context.current())
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method.value)
            .setAttribute(UrlAttributes.URL_PATH, request.url.toString())
            .startSpan()
        val scope = span.makeCurrent()
        propagator.inject(Context.current(), request, KtorClientTextMapSetter)

        request.attributes.put(SPAN_ATTRIBUTE_KEY, span)
        request.attributes.put(SCOPE_ATTRIBUTE_KEY, scope)
    }

    onResponse { response ->
        if (!openTelemetrySet) return@onResponse

        val span = response.call.request.attributes[SPAN_ATTRIBUTE_KEY]
        val scope = response.call.request.attributes[SCOPE_ATTRIBUTE_KEY]

        span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.status.value)
        if (response.status.value >= 500) {
            span.setStatus(StatusCode.ERROR)
        } else {
            span.setStatus(StatusCode.OK)
        }
        span.end()
        scope.close()
    }

    // TODO(Johann) what is there is a request error? And you don't end up handling onResponse?
}