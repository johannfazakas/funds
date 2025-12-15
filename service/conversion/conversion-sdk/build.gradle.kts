plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":conversion:conversion-api"))
    api(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
