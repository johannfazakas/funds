plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(project(":commons:commons-sdk"))
    api(project(":account:account-api"))
    testImplementation(project(":commons:commons-test"))
}
