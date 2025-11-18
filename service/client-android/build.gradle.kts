plugins {
    id("funds.android-application-conventions")
}

dependencies {
    implementation(project(":client-shared:client-shared-api"))
    implementation(project(":client-shared:client-shared-sdk"))
    implementation(project(":client-shared:client-shared-core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("co.touchlab:kermit:2.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
