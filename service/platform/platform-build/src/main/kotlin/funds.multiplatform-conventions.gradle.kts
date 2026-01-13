import com.android.build.gradle.LibraryExtension as AndroidLibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

group = rootProject.group
version = rootProject.version


plugins {
    id("funds.common-conventions")
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

repositories {
    google()
}

val libs = the<VersionCatalogsExtension>().named("libs")

configure<AndroidLibraryExtension> {
    namespace = "ro.jf.funds"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configure<KotlinMultiplatformExtension> {
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
                implementation(libs.findLibrary("kotlinx-serialization-json").get())
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
                implementation(libs.findLibrary("kotlinx-datetime").get())
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
                implementation(libs.findLibrary("kotlinx-coroutines-android").get())
            }
        }

        val jsMain by getting
        val jsTest by getting
    }
}
