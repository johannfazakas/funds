plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":reporting:reporting-api"))
    api(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
