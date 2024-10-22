import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("funds.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":import:import-api"))
    implementation(project(":commons:commons-service"))
    implementation(project(":account:account-sdk"))
    implementation(project(":fund:fund-sdk"))
    implementation(libs.kotlin.csv)
    testImplementation(project(":commons:commons-test"))
}

application {
    mainClass.set("ro.jf.funds.importer.service.ImportServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/import-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
