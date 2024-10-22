plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":reporting:reporting-api"))
    implementation(project(":commons-sdk"))
    testImplementation(project(":commons-test"))
}
