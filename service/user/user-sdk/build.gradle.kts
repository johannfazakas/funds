plugins {
    id("funds.jvm-library-conventions")
}

dependencies {
    api(project(":user:user-api"))
    api(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
