plugins {
    id("funds.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons"))
    api(project(":account-api"))
    api(project(":fund-api"))
}
