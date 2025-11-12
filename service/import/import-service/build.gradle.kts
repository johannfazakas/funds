plugins {
    id("funds.kotlin-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":import:import-api"))
    implementation(project(":commons:commons"))
    implementation(project(":fund:fund-sdk"))
    implementation(project(":conversion:conversion-sdk"))
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
