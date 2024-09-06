plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    // TODO(Johann) probably not all dependencies are required
    api(project(":import-api"))
    api(project(":commons"))

    // TODO(Johann) some might be moved to the common module
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.kotlin.logging)

    testApi(project(":commons-test"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.jupiter)

    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.bom)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mockserver)
    testImplementation(libs.mockserver.client)
}
