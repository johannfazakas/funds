plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":user-api"))
    implementation(project(":commons-sdk"))
    testImplementation(project(":commons-test"))
}
