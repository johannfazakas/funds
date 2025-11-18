plugins {
    id("funds.kotlin-multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.benasher44:uuid:0.8.4")
            }
        }
    }
}
