plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "funds"

include("commons:commons")
include("commons:commons-test")

include("historical-pricing:historical-pricing-api")
include("historical-pricing:historical-pricing-sdk")
include("historical-pricing:historical-pricing-service")

include("user:user-api")
include("user:user-sdk")
include("user:user-service")

include("account:account-api")
include("account:account-sdk")
include("account:account-service")

include("fund:fund-api")
include("fund:fund-sdk")
include("fund:fund-service")

include("import:import-api")
include("import:import-sdk")
include("import:import-service")

include("reporting:reporting-api")
include("reporting:reporting-sdk")
include("reporting:reporting-service")
