plugins {
    id("funds.kotlin-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":reporting:reporting-api"))
    implementation(project(":commons:commons-jvm"))
    implementation(project(":fund:fund-sdk"))
    implementation(project(":conversion:conversion-sdk"))
    implementation(libs.big.math)
    implementation(libs.kotlin.statistics)
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
