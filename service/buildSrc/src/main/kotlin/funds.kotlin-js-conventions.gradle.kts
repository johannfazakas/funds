group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
    google()
}

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jsMain by getting
        val jsTest by getting
    }
}

tasks.register("installLocal") {
    group = "build"
    description = "Build the artifact and publish it to local maven"

    dependsOn("build", "publishToMavenLocal")
}
