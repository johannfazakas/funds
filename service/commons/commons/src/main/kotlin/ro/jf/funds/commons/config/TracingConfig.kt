package ro.jf.funds.commons.config

import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.ktor.KtorServerTracing
import io.opentelemetry.api.common.AttributeKey as OpenTelemetryAttributeKey

private const val OBSERVABILITY_ENABLED_KEY = "observability.enabled"
private const val OTEL_COLLECTOR_GRPC_ENDPOINT_KEY = "observability.otel-collector.grpc-endpoint"
private const val SERVICE_NAME_KEY = "ktor.application.id"
private val SERVICE_NAME_ATTRIBUTE_KEY = OpenTelemetryAttributeKey.stringKey("service.name")


private val logger = logger { }

fun Application.configureTracing() {
    val observabilityEnabled = environment.getBooleanPropertyOrNull(OBSERVABILITY_ENABLED_KEY) ?: false
    if (!observabilityEnabled) {
        logger.info { "Observability is disabled, skipping OpenTelemetry initialization." }
        return
    }
    initOpenTelemetry()
    install(KtorServerTracing)
}

private fun Application.initOpenTelemetry(): OpenTelemetry {
    val resource = Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.of(
                    SERVICE_NAME_ATTRIBUTE_KEY,
                    environment.getStringProperty(SERVICE_NAME_KEY)
                )
            )
        )
    val tracerProvider: SdkTracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(
            BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint(environment.getStringProperty(OTEL_COLLECTOR_GRPC_ENDPOINT_KEY))
                    .build()
            ).build()
        )
        .build()

    val openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(
            ContextPropagators.create(
                TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(),
                    W3CBaggagePropagator.getInstance()
                )
            )
        )
        .buildAndRegisterGlobal()
    logger.info { "OpenTelemetry initialized with service name: ${resource.attributes.get(SERVICE_NAME_ATTRIBUTE_KEY)}" }
    return openTelemetrySdk
}
