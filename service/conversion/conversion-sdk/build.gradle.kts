plugins {
    alias(libs.plugins.funds.jvm.library)
}

dependencies {
    api(project(":conversion:conversion-api"))
    api(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}
