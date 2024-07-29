plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    api(project(":account-api"))
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

    implementation(libs.assertj)

    implementation(libs.kotlin.test.junit)
    implementation(libs.junit.jupiter)

    implementation(libs.testcontainers.bom)
    implementation(libs.testcontainers.junit)
    implementation(libs.testcontainers.mockserver)
    implementation(libs.mockserver.client)
}
