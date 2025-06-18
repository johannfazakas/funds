package ro.jf.funds.reporting.service.config

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
import ro.jf.funds.commons.config.getStringProperty

private val SERVICE_NAME_KEY = "ktor.application.id"
private val OTEL_COLLECTOR_GRPC_ENDPOINT_KEY = "observability.otel-collector.grpc-endpoint"

fun Application.configureTracing() {
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
                    AttributeKey.stringKey("service.name"),
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

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal()
}

