import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("funds.kotlin-application-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":reporting:reporting-api"))
    implementation(project(":commons:commons-service"))
    implementation(project(":fund:fund-sdk"))
    implementation(project(":historical-pricing:historical-pricing-sdk"))
    testImplementation(project(":commons:commons-test"))
}

application {
    mainClass.set("ro.jf.funds.reporting.service.ReportingServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/reporting-service")
    }
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
