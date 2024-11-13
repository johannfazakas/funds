plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    implementation(project(":commons:commons-api"))
    api(project(":fund:fund-api"))
    testImplementation(project(":commons:commons-test"))
}
