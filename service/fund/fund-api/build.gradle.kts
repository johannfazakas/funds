plugins {
    id("funds.kotlin-multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons:commons-api"))
                api(libs.bignum)
            }
        }
    }
}
