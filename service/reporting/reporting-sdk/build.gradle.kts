plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":reporting:reporting-api"))
    implementation(project(":commons:commons-api"))
    testImplementation(project(":commons:commons-test"))
}
