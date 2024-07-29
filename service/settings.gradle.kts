plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "bookkeeper-api"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("kotlin", "2.0.0")
            version("ktor", "2.3.2")
            version("koin", "3.5.6")
            version("exposed", "0.41.1")
            version("testcontainers", "1.19.8")

            plugin("ktor", "io.ktor.plugin").versionRef("ktor")
            plugin("kotlin-serialization", "org.jetbrains.kotlin.plugin.serialization").version("2.0.0")

            // ktor libraries
            library(
                "ktor-server-content-negotiation",
                "io.ktor",
                "ktor-server-content-negotiation-jvm"
            ).versionRef("ktor")
            library("ktor-server-core", "io.ktor", "ktor-server-core-jvm").versionRef("ktor")
            library("ktor-server-netty", "io.ktor", "ktor-server-netty-jvm").versionRef("ktor")
            library("ktor-server-yaml", "io.ktor", "ktor-server-config-yaml").versionRef("ktor")
            library("ktor-client-core", "io.ktor", "ktor-client-core").versionRef("ktor")
            library("ktor-client-cio", "io.ktor", "ktor-client-cio").versionRef("ktor")
            library("ktor-client-json", "io.ktor", "ktor-client-json").versionRef("ktor")
            library("ktor-client-serialization", "io.ktor", "ktor-client-serialization").versionRef("ktor")
            library("ktor-client-content-negotiation", "io.ktor", "ktor-client-content-negotiation").versionRef("ktor")
            library("ktor-serialization-kotlinx-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")

            // kotlinx libraries
            library(
                "kotlinx-serialization-json",
                "org.jetbrains.kotlinx",
                "kotlinx-serialization-json-jvm"
            ).version("1.6.3")
            library("kotlinx-datetime", "org.jetbrains.kotlinx", "kotlinx-datetime").version("0.6.0")

            // koin
            library("koin-core", "io.insert-koin", "koin-core").versionRef("koin")
            library("koin-ktor", "io.insert-koin", "koin-ktor").versionRef("koin")

            // db
            library("postgresql", "org.postgresql", "postgresql").version("42.7.3")
            library("flyway", "org.flywaydb", "flyway-core").version("6.5.2")
            library("exposed-core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
            library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")
            library("exposed-java-time", "org.jetbrains.exposed", "exposed-java-time").versionRef("exposed")
            library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")

            // logging libraries
            library("kotlin-logging", "io.github.microutils", "kotlin-logging-jvm").version("3.0.5")
            library("logback", "ch.qos.logback", "logback-classic").version("1.4.14")

            // test libraries
            library("junit.jupiter", "org.junit.jupiter", "junit-jupiter").version("5.9.3")
            library("junit.platform.launcher", "org.junit.platform", "junit-platform-launcher").version("1.8.2")
            library("kotlin-test", "org.jetbrains.kotlin", "kotlin-test").versionRef("kotlin")
            library("kotlin-test-junit", "org.jetbrains.kotlin", "kotlin-test-junit").versionRef("kotlin")
            library("ktor-server-tests-jvm", "io.ktor", "ktor-server-tests-jvm").versionRef("ktor")
            library("ktor-server-test-host", "io.ktor", "ktor-server-test-host").versionRef("ktor")
            library("assertj", "org.assertj", "assertj-core").version("3.26.0")
            library("testcontainers-bom", "org.testcontainers", "testcontainers-bom").versionRef("testcontainers")
            library("testcontainers-junit", "org.testcontainers", "junit-jupiter").versionRef("testcontainers")
            library("testcontainers-postgresql", "org.testcontainers", "postgresql").versionRef("testcontainers")
            library("testcontainers-jdbc", "org.testcontainers", "jdbc").versionRef("testcontainers")
            library("testcontainers-mockserver", "org.testcontainers", "mockserver").versionRef("testcontainers")
            library("mockserver-client", "org.mock-server", "mockserver-client-java").version("5.15.0")
        }
    }
}

include("commons")
include("commons-service")
include("commons-test")

include("historical-pricing-api")
include("historical-pricing-sdk")
include("historical-pricing-service")

include("investment-api")
include("investment-sdk")
include("investment-service")

include("user-api")
include("user-sdk")
include("user-service")

include("account-api")
include("account-sdk")
include("account-service")

include("fund-api")
include("fund-sdk")
include("fund-service")
