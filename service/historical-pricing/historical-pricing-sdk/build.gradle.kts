plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":historical-pricing:historical-pricing-api"))
    implementation(project(":commons:commons"))
    testImplementation(project(":commons:commons-test"))

    // TODO(Johann) is this needed? ktor client should be used instead
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
