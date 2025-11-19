plugins {
    id("funds.kotlin-service-conventions")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":user:user-api"))
    implementation(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
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
