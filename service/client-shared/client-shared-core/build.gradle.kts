plugins {
    id("funds.kotlin-multiplatform-conventions")
    // TODO(Johann) is this required here? Couldn't it have been added to the convention plugin?
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":import:import-api"))
                implementation(project(":client-shared:client-shared-api"))
                implementation(project(":client-shared:client-shared-sdk"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("co.touchlab:kermit:2.0.4")
                // TODO(Johann) should be added to `commons`. there could be a separate commons-service maybe.
                implementation(libs.uuid)
            }
        }
    }
}
