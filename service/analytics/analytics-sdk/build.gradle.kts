plugins {
    alias(libs.plugins.funds.jvm.library)
}

dependencies {
    api(project(":service:analytics:analytics-api"))
    api(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}
