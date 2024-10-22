plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons:commons-api"))
}
