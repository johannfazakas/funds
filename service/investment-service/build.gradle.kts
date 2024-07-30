import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val postgresql_driver_version: String by project
val koin_version: String by project
val kotlin_logging_version: String by project

plugins {
    id("bookkeeper.kotlin-application-conventions")
    id("io.ktor.plugin") version "2.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

group = "ro.jf.bookkeeper"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":investment-api"))

    // Johann! extract dependencies that make sense into commons constraints
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")

    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-json")
    implementation("io.ktor:ktor-client-serialization")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    implementation("io.insert-koin:koin-core:$koin_version")
    implementation("io.insert-koin:koin-ktor:$koin_version")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlin_logging_version")

    implementation("org.postgresql:postgresql:$postgresql_driver_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")

    implementation("io.ktor:ktor-server-config-yaml")

    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("funds/investment-service")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
