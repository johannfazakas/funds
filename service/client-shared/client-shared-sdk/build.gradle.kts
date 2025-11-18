plugins {
    id("funds.kotlin-multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // TODO(Johann)
                implementation(project(":client-shared:client-shared-api"))
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                implementation("co.touchlab:kermit:2.0.4")
                implementation(libs.uuid)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.3")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.0.3")
            }
        }
    }
}
