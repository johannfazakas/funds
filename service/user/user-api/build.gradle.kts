plugins {
    id("funds.kotlin-multiplatform-conventions")
    // TODO(Johann) this could be part of the convention plugin
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        // TODO(Johann) should be extracted to commons-api
        val commonMain by getting {
            dependencies {
                implementation("com.benasher44:uuid:0.8.4")
            }
        }
    }
}
