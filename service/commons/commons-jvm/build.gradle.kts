plugins {
    id("funds.jvm-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

// TODO(Johann) how about renaming commons to platform? and I'm not sure about -jvm, but -sdk or -service might be more restrictive. should it be split?
dependencies {
    api(project(":commons:commons-api"))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    api(libs.koin.core)
    api(libs.koin.ktor)

    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.core)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.netty)
    api(libs.ktor.server.yaml)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.cors)

    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.serialization)
    api(libs.ktor.client.json)
    api(libs.ktor.client.content.negotiation)

    api(libs.postgresql)
    api(libs.hikari)
    api(libs.flyway)
    api(libs.flyway.postgres)

    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.exposed.dao)
    api(libs.exposed.json)

    api(libs.kafka.clients)

    api(libs.kotlin.logging)
    api(libs.logback)

    api(libs.opentelemetry.api)
    api(libs.opentelemetry.sdk)
    api(libs.opentelemetry.exporter.otlp)
    api(libs.opentelemetry.extension.kotlin)
    api(libs.opentelemetry.semconv)

    testImplementation(project(":commons:commons-test"))
}
