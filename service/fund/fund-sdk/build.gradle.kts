plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":fund:fund-api"))
    api(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}
