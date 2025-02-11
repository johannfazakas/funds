
plugins {
    id("funds.kotlin-library-conventions")
}

dependencies {
    api(project(":historical-pricing:historical-pricing-api"))
    implementation(project(":commons:commons"))
    testImplementation(project(":commons:commons-test"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
