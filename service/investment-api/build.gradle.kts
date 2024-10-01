plugins {
    id("bookkeeper.kotlin-library-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

// TODO(Johann) is this service/api/sdk required?
dependencies {
    api(project(":commons"))
    testApi(project(":commons-test"))
}
