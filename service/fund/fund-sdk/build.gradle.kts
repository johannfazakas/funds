plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":fund:fund-api"))
    implementation(project(":commons:commons-jvm"))
    testImplementation(project(":commons:commons-test"))
}
