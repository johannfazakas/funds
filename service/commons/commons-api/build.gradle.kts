plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // TODO(Johann) revisit these
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

    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.serialization)
    api(libs.ktor.client.json)
    api(libs.ktor.client.content.negotiation)

    api(libs.postgresql)
    api(libs.flyway)

    api(libs.kafka.clients)

    api(libs.kotlin.logging)
    api(libs.logback)
}
