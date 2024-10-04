
plugins {
    id("bookkeeper.kotlin-library-conventions")
}

dependencies {
    api(project(":historical-pricing-api"))
    implementation(project(":commons-sdk"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
