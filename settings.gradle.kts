pluginManagement {
    includeBuild("platform/platform-build")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "funds"

include("platform:platform-api")
include("platform:platform-jvm")
include("platform:platform-jvm-test")

include("service:conversion:conversion-api")
include("service:conversion:conversion-sdk")
include("service:conversion:conversion-service")

include("service:user:user-api")
include("service:user:user-sdk")
include("service:user:user-service")

include("service:fund:fund-api")
include("service:fund:fund-sdk")
include("service:fund:fund-service")

include("service:import:import-api")
include("service:import:import-sdk")
include("service:import:import-service")

include("service:reporting:reporting-api")
include("service:reporting:reporting-sdk")
include("service:reporting:reporting-service")

include("client:client-sdk")
include("client:web-client")
include("client:android-client")
