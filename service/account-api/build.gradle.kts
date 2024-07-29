plugins {
    id("bookkeeper.kotlin-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":commons"))

    // TODO(Johann) why can't this be moved to commons?
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testApi(project(":commons-test"))
}
