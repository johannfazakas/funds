plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    implementation(libs.kotlin.test.junit)
    implementation(libs.junit.jupiter)

    implementation(libs.ktor.server.tests.jvm)
    implementation(libs.ktor.server.test.host)

    implementation(libs.testcontainers.bom)
    implementation(libs.testcontainers.junit)
    implementation(libs.testcontainers.mockserver)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.jdbc)
    implementation(libs.mockserver.client)

    implementation(libs.flyway)
}
