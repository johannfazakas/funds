plugins {
    id("bookkeeper.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":commons"))

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.yaml)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    implementation(libs.postgresql)
    implementation(libs.flyway)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.dao)
}
