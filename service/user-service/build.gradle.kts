import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("bookkeeper.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "ro.jf.bk"
version = "0.0.1"

dependencies {
    api(project(":user-api"))
    api(project(":commons-service"))

    implementation(libs.kotlinx.datetime)

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.yaml)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.json)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    implementation(libs.postgresql)
    implementation(libs.flyway)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.dao)

    implementation(libs.kotlin.logging)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.tests.jvm)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlin.test.junit)
    // Johann! extract in commons, extract versions
    testImplementation(libs.testcontainers.bom)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.jdbc)
}

application {
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("bookkeeper/user-api")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
