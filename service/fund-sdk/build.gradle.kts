plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(project(":commons-sdk"))
    api(project(":fund-api"))
    testImplementation(project(":commons-test"))
}
