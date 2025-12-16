plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":user:user-api"))
    api(project(":platform:platform-jvm"))
    testImplementation(project(":platform:platform-jvm-test"))
}
