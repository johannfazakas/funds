plugins {
    id("bookkeeper.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons"))
    testApi(project(":commons-test"))
}
