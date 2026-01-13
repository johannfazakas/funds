plugins {
    alias(libs.plugins.funds.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":platform:platform-api"))
                api(libs.bignum)
            }
        }
    }
}
