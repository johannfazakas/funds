plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    api(libs.ktor.server.core)

    api(libs.postgresql)

    api(libs.kafka.clients)

    api(libs.kotlin.logging)
    api(libs.logback)
}
