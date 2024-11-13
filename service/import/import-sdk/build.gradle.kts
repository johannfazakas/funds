plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":import:import-api"))
    implementation(project(":commons:commons-api"))
    testImplementation(project(":commons:commons-test"))
}
