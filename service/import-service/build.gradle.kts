import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("bookkeeper.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // TODO(Johann) probably not all are needed
    api(project(":import-api"))
    api(project(":commons-service"))
    api(project(":fund-sdk"))
    api(project(":fund-api"))

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

    implementation(libs.kotlin.logging)
    implementation(libs.logback)

    testApi(project(":commons-test"))

    // TODO(Johann) will these be needed? if extracted to commons-test?
    testImplementation(libs.ktor.server.tests.jvm)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlin.mockito)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.testcontainers.bom)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.jdbc)
    testImplementation(libs.testcontainers.mockserver)
    testImplementation(libs.mockserver.client)
}

application {
    mainClass.set("ro.jf.funds.importer.service.ImportServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("funds/import-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
