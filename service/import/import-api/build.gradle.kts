plugins {
    id("funds.multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons:commons-api"))
                api(project(":fund:fund-api"))
            }
        }
    }
}
