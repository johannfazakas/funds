import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("bookkeeper.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":fund-api"))
    api(project(":commons-service"))
    api(project(":account-sdk"))
    api(project(":account-api"))

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
    mainClass.set("ro.jf.bk.fund.service.FundServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("funds/fund-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
