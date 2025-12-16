plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "funds"

include("platform:platform-api")
include("platform:platform-jvm")
include("platform:platform-jvm-test")

include("conversion:conversion-api")
include("conversion:conversion-sdk")
include("conversion:conversion-service")

include("user:user-api")
include("user:user-sdk")
include("user:user-service")

include("fund:fund-api")
include("fund:fund-sdk")
include("fund:fund-service")

include("import:import-api")
include("import:import-sdk")
include("import:import-service")

include("reporting:reporting-api")
include("reporting:reporting-sdk")
include("reporting:reporting-service")

include("client:client-sdk")
include("client:client-web")
include("client:client-android")
