import gradle.kotlin.dsl.accessors._0ca2999132398b98e46376b5f7cd33f7.publishing

group = rootProject.group
version = rootProject.version

// TODO(Johann) shouldn't this convention plugin inherit from common conventions?
repositories {
    mavenCentral()
    mavenLocal()
    google()
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

android {
    namespace = "ro.jf.funds.client.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()

    androidTarget {
        publishLibraryVariants("release")
    }

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

        val jvmMain by getting
        val jvmTest by getting

        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
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

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])

            groupId = group.toString()
            artifactId = project.name
            version = rootProject.version.toString()
        }
    }
}
