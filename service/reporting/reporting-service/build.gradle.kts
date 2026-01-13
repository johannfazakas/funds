plugins {
    alias(libs.plugins.funds.jvm.service)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":service:reporting:reporting-api"))
    implementation(project(":platform:platform-jvm"))
    implementation(project(":service:fund:fund-sdk"))
    implementation(project(":service:conversion:conversion-sdk"))
    implementation(libs.big.math)
    implementation(libs.kotlin.statistics)
    testImplementation(project(":platform:platform-jvm-test"))
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
