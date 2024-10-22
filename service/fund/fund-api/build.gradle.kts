plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":commons:commons-api"))
}
