plugins {
    id("bookkeeper.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.kotlin.logging)
    implementation(libs.logback)
}
