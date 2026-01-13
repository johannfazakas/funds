plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

val libs = versionCatalogs.named("libs")

dependencies {
    implementation(libs.findLibrary("kotlin-gradle-plugin").get())
    implementation(libs.findLibrary("compose-gradle-plugin").get())
    implementation(libs.findLibrary("android-gradle-plugin").get())
}
