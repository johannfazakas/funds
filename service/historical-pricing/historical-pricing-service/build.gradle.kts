import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// TODO(Johann) remove these, use common versions from libs/toml
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val postgresql_driver_version: String by project
val koin_version: String by project
val kotlin_logging_version: String by project
val jsoup_version: String by project
val poi_version: String by project

plugins {
    // TODO(Johann) align service with others, use common versions
    id("funds.kotlin-application-conventions")
    id("io.ktor.plugin") version "2.3.12"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":historical-pricing:historical-pricing-api"))
    implementation(project(":commons:commons-service"))

    implementation("org.jsoup:jsoup:$jsoup_version")
    implementation("org.apache.poi:poi-ooxml:$poi_version")

    testImplementation(project(":commons:commons-test"))
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/historical-pricing-service")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
