plugins {
    id("funds.multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.uuid)
                api(libs.bignum)
            }
        }
    }
}
