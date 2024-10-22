plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons:commons-api"))

    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.core)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.netty)
    api(libs.ktor.server.yaml)

    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.serialization)
    api(libs.ktor.client.json)
    api(libs.ktor.client.content.negotiation)

    api(libs.koin.core)
    api(libs.koin.ktor)

    api(libs.postgresql)
    api(libs.flyway)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.exposed.dao)
}
