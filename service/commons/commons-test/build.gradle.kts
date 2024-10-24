plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    api(libs.kotlin.test.junit)
    api(libs.junit.jupiter)
    api(libs.kotlin.mockito)
    api(libs.assertj)

    api(libs.exposed.core)
    api(libs.flyway)

    api(libs.ktor.server.tests.jvm)
    api(libs.ktor.server.test.host)
    implementation(libs.ktor.server.content.negotiation)

    api(libs.testcontainers.bom)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.mockserver)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.jdbc)
    api(libs.mockserver.client)

}
