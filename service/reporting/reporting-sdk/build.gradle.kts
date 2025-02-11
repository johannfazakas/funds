plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":reporting:reporting-api"))
    implementation(project(":commons:commons"))
    testImplementation(project(":commons:commons-test"))
}
