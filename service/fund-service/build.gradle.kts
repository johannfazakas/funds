import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("bookkeeper.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":fund-api"))
    implementation(project(":commons-service"))
    implementation(project(":account-sdk"))
    testImplementation(project(":commons-test"))
}

application {
    mainClass.set("ro.jf.bk.fund.service.FundServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    // TODO(Johann) check build image & publish tasks dependencies
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/fund-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
