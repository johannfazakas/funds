plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.20")
    implementation("com.android.tools.build:gradle:8.7.0")
}

configurations.all {
    resolutionStrategy {
        force("org.apache.commons:commons-compress:1.26.0")
    }
}
