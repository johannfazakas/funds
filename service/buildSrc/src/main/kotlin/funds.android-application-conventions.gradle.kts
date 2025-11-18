group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
    google()
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 35
    namespace = "ro.jf.funds.client.android"

    defaultConfig {
        applicationId = "ro.jf.funds.client.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = rootProject.version.toString()

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
