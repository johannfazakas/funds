plugins {
    alias(libs.plugins.funds.multiplatform)
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
