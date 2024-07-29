plugins {
    id("bookkeeper.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
