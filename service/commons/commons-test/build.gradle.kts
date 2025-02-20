dependencies {
    implementation(project(":commons:commons"))

    api(libs.kotlin.test.junit)
    api(libs.junit.jupiter)
    api(libs.assertj)
    api(libs.kotlin.mockito)

    api(libs.ktor.server.tests.jvm)
    api(libs.ktor.server.test.host)

    api(libs.testcontainers.bom)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.mockserver)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.jdbc)
    api(libs.testcontainers.kafka)

    api(libs.mockserver.client)
}
