plugins {
    alias(libs.plugins.funds.jvm.service)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":service:conversion:conversion-api"))
    implementation(project(":platform:platform-jvm"))

    implementation(libs.jsoup)
    implementation(libs.poi)

    testImplementation(project(":platform:platform-jvm-test"))
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/conversion-service")
    }
}
