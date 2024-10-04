plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    api(libs.kotlin.test.junit)
    api(libs.junit.jupiter)
    api(libs.kotlin.mockito)
    api(libs.assertj)

    // TODO(Johann) is this required here?
    api(libs.exposed.core)

    api(libs.ktor.server.tests.jvm)
    api(libs.ktor.server.test.host)

    api(libs.testcontainers.bom)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.mockserver)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.jdbc)
    api(libs.mockserver.client)

    // TODO(Johann) is this required here?
    api(libs.flyway)
}
