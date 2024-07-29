plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    api(project(":investment-api"))
}
