plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":reporting:reporting-api"))
    api(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}
