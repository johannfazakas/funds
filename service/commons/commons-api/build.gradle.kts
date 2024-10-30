plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    // TODO(Johann) not ideal to add this here
    api(libs.kafka.clients)

    api(libs.kotlin.logging)
    api(libs.logback)
}
