plugins {
    id("funds.kotlin-service-conventions")
    /*
    TODO(Johann) could convention plugins be referenced in a type-safe way?
    [plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "1.9.20" }
kotlin-common-conventions = { id = "funds.kotlin-common-conventions", version = "unspecified" }
spring-conventions = { id = "funds.spring-conventions", version = "unspecified" }
     */
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":fund:fund-api"))
    implementation(project(":commons:commons-jvm"))
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
