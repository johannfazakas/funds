plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    api(project(":import-api"))
    implementation(project(":commons-sdk"))
    testImplementation(project(":commons-test"))
}
