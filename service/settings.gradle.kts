plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "funds"

// TODO(Johann) could group modules in subfolders?
include("commons")
include("commons-service")
include("commons-sdk")
include("commons-test")

include("historical-pricing-api")
include("historical-pricing-sdk")
include("historical-pricing-service")

include("user-api")
include("user-sdk")
include("user-service")

include("account-api")
include("account-sdk")
include("account-service")

include("fund-api")
include("fund-sdk")
include("fund-service")

include("import-api")
include("import-sdk")
include("import-service")

include("reporting:reporting-api")
include("reporting:reporting-sdk")
include("reporting:reporting-service")
