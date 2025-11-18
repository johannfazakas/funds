plugins {
    id("funds.kotlin-js-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":client-shared:client-shared-api"))
                implementation(project(":client-shared:client-shared-sdk"))
                implementation(project(":client-shared:client-shared-core"))
            }
        }
    }
}
