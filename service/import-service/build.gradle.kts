import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("bookkeeper.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":import-api"))
    implementation(project(":commons-service"))
    implementation(project(":account-sdk"))
    implementation(project(":fund-sdk"))
    implementation(libs.kotlin.csv)
    testImplementation(project(":commons-test"))
}

application {
    mainClass.set("ro.jf.funds.importer.service.ImportServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("funds/import-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
