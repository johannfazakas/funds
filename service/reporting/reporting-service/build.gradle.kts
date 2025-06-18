plugins {
    id("funds.kotlin-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":reporting:reporting-api"))
    implementation(project(":commons:commons"))
    implementation(project(":fund:fund-sdk"))
    implementation(project(":historical-pricing:historical-pricing-sdk"))
    testImplementation(project(":commons:commons-test"))

    implementation("io.opentelemetry:opentelemetry-api:1.48.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.48.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:2.16.0-alpha")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.48.0")
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
