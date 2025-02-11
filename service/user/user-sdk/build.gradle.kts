plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":user:user-api"))
    implementation(project(":commons:commons"))
    testImplementation(project(":commons:commons-test"))
}
