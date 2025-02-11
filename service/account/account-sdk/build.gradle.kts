plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(project(":commons:commons"))
    api(project(":account:account-api"))
    testImplementation(project(":commons:commons-test"))
}
