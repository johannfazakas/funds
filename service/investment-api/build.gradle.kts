plugins {
    id("bookkeeper.kotlin-library-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}
