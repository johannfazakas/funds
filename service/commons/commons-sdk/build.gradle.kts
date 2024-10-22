plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

// TODO(Johann) is this required or could be moved as a library convention in buildSrc? Or is buildSrc redundant?
dependencies {
    api(project(":commons:commons-api"))

    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.serialization)
    api(libs.ktor.client.json)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
}
