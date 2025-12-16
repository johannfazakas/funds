plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":import:import-api"))
    api(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
