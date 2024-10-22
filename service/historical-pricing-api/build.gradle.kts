plugins {
    id("funds.kotlin-library-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

dependencies {
    api(project(":commons"))
}
