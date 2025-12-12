plugins {
    id("funds.kotlin-js-conventions")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js(IR) {
        generateTypeScriptDefinitions()
    }

    // TODO(Johann) create a cleanInstall task
    sourceSets {
        val jsMain by getting {
            dependencies {
                // TODO(Johann) extract deps in toml?
                implementation(project(":client:client-sdk"))
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-js:3.0.3")

                implementation(npm("react", "18.3.1"))
                implementation(npm("react-dom", "18.3.1"))
                implementation(npm("react-router-dom", "6.28.0"))

                implementation(devNpm("ts-loader", "9.5.1"))
                implementation(devNpm("@types/react", "18.3.12"))
                implementation(devNpm("@types/react-dom", "18.3.1"))
                implementation(devNpm("html-webpack-plugin", "5.6.0"))
            }
        }
    }
}

tasks.named<Sync>("jsBrowserDistribution") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
