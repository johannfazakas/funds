plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":user:user-api"))
    implementation(project(":commons:commons-api"))
    testImplementation(project(":commons:commons-test"))
}
