package ro.jf.funds.commons.config

import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import mu.KotlinLogging.logger

private const val OBSERVABILITY_ENABLED_KEY = "observability.enabled"
private const val OTEL_COLLECTOR_GRPC_ENDPOINT_KEY = "observability.otel-collector.grpc-endpoint"
private const val SERVICE_NAME_KEY = "ktor.application.id"
private val SERVICE_NAME_ATTRIBUTE_KEY = AttributeKey.stringKey("service.name")


private val logger = logger { }

fun Application.configureTracing() {
    val observabilityEnabled = environment.getBooleanPropertyOrNull(OBSERVABILITY_ENABLED_KEY) ?: false
    if (!observabilityEnabled) return
    val openTelemetry = initOpenTelemetry()
    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
    }
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
        .buildAndRegisterGlobal()
    logger.info { "OpenTelemetry initialized with service name: ${resource.attributes.get(SERVICE_NAME_ATTRIBUTE_KEY)}" }
    return openTelemetrySdk
}
