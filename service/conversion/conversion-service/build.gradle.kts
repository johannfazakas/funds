plugins {
    id("funds.jvm-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":conversion:conversion-api"))
    implementation(project(":commons:commons-jvm"))

    implementation(libs.jsoup)
    implementation(libs.poi)

    testImplementation(project(":commons:commons-test"))
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
