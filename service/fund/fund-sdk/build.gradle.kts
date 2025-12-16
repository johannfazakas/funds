plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":fund:fund-api"))
    api(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
