plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
    google()
}

kotlin {
    js(IR) {
        generateTypeScriptDefinitions()
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

        val jsMain by getting {
            dependencies {
                implementation(project(":client:client-sdk"))
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-js:3.0.3")

                implementation(npm("react", "18.3.1"))
                implementation(npm("react-dom", "18.3.1"))
                implementation(npm("react-router-dom", "6.28.0"))
                implementation(npm("recharts", "2.12.7"))

                implementation(devNpm("ts-loader", "9.5.1"))
                implementation(devNpm("@types/react", "18.3.12"))
                implementation(devNpm("@types/react-dom", "18.3.1"))
                implementation(devNpm("html-webpack-plugin", "5.6.0"))

                implementation(devNpm("tailwindcss", "3.4.1"))
                implementation(devNpm("postcss", "8.4.35"))
                implementation(devNpm("postcss-loader", "8.1.0"))
                implementation(devNpm("autoprefixer", "10.4.17"))

                implementation(npm("class-variance-authority", "0.7.0"))
                implementation(npm("clsx", "2.1.0"))
                implementation(npm("tailwind-merge", "2.2.1"))
                implementation(npm("lucide-react", "0.344.0"))
                implementation(npm("@radix-ui/react-dialog", "1.0.5"))
                implementation(npm("@radix-ui/react-select", "2.0.0"))
                implementation(npm("@radix-ui/react-popover", "1.0.7"))
                implementation(npm("cmdk", "0.2.1"))
                implementation(npm("@radix-ui/react-label", "2.0.2"))
                implementation(npm("@radix-ui/react-slot", "1.0.2"))
                implementation(npm("@dnd-kit/core", "6.1.0"))
                implementation(npm("@dnd-kit/sortable", "8.0.0"))
                implementation(npm("@dnd-kit/utilities", "3.2.2"))
                implementation(devNpm("tailwindcss-animate", "1.0.7"))
            }
        }

        val jsTest by getting
    }
}

tasks.named<Sync>("jsBrowserDistribution") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Exec>("buildDockerImage") {
    group = "docker"
    description = "Build the Docker image for the web application"
    dependsOn("jsBrowserProductionWebpack")

    workingDir(projectDir)
    commandLine("docker", "build", "-t", "funds/web-client-react:latest", ".")
}

tasks.register("installLocal") {
    group = "build"
    description = "Build the web application and create a Docker image"
    dependsOn("build", "buildDockerImage")
}
