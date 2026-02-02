plugins {
    alias(libs.plugins.funds.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":service:fund:fund-api"))
                api(project(":service:user:user-api"))
                api(project(":service:reporting:reporting-api"))
                api(project(":platform:platform-api"))
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
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
