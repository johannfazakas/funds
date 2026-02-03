import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kobweb.application)
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
    google()
}

kotlin {
    configAsKobwebApplication()

    sourceSets {
        jsMain.dependencies {
            implementation(project(":client:client-sdk"))
            implementation(project(":service:fund:fund-api"))
            implementation(project(":service:user:user-api"))

            implementation(libs.compose.html.core)
            implementation(libs.compose.runtime)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.silk.icons.fa)

            implementation(libs.kotlinx.coroutines.core)
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-js:3.0.3")
        }
    }
}
