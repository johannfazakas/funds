plugins {
    alias(libs.plugins.funds.jvm.service)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":user:user-api"))
    implementation(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}

application {
    mainClass.set("ro.jf.funds.user.service.UserServiceApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("funds/user-service")
    }
}
