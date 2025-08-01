plugins {
    id("funds.kotlin-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":fund:fund-api"))
    implementation(project(":commons:commons"))
    implementation(project(":account:account-sdk"))
    testImplementation(project(":commons:commons-test"))
}

application {
    mainClass.set("ro.jf.funds.fund.service.FundServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/fund-service")
    }
}
