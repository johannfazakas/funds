package ro.jf.funds.commons.observability.tracing.ktor

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import mu.KotlinLogging.logger

private const val INSTRUMENTATION_SCOPE_NAME = "ro.jf.funds.ktor.server"
private const val INSTRUMENTATION_SCOPE_VERSION = "1.0.0"

val SPAN_KEY = AttributeKey<Span>("otel-span")
val SCOPE_KEY = AttributeKey<Scope>("otel-scope")

private object KtorServerTextMapGetter : TextMapGetter<ApplicationRequest> {
    override fun keys(carrier: ApplicationRequest): Iterable<String> = carrier.headers.names()
    override fun get(carrier: ApplicationRequest?, key: String): String? = carrier?.headers?.get(key)
}

val KtorServerTracing = createApplicationPlugin(name = "FundsKtorServerTracing") {
    val tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME, INSTRUMENTATION_SCOPE_VERSION)
    val propagator = GlobalOpenTelemetry.get().propagators.textMapPropagator

    onCall { call ->
        val parent = propagator.extract(Context.current(), call.request, KtorServerTextMapGetter)

        val span = tracer.spanBuilder("${call.request.httpMethod.value}:${call.request.uri}")
            .setSpanKind(SpanKind.SERVER)
            .setParent(parent)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, call.request.httpMethod.value)
            .setAttribute(UrlAttributes.URL_PATH, call.request.uri)
            .startSpan()
        val scope = span.makeCurrent()
        call.attributes.put(SPAN_KEY, span)
        call.attributes.put(SCOPE_KEY, scope)
    }

    on(ResponseSent) { call ->
        call.attributes.getOrNull(SPAN_KEY)?.apply {
            call.response.status()?.let { statusCode ->
                setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode.value)
            }
            setStatus(StatusCode.OK)
            end()
        }
        call.attributes.getOrNull(SCOPE_KEY)?.close()
    }

    on(CallFailed) { call, cause ->
        call.attributes.getOrNull(SPAN_KEY)?.apply {
            recordException(cause)
            setStatus(StatusCode.ERROR)
            end()
        }
        call.attributes.getOrNull(SCOPE_KEY)?.close()
    }
}
