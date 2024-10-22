plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(project(":commons-sdk"))
    api(project(":account-api"))
    testImplementation(project(":commons-test"))
}
