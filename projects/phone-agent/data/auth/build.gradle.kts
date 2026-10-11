plugins {
    id("phoneagent.android.library")
}

android {
    namespace = "com.example.phoneagent.data.auth"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(testFixtures(project(":core:domain")))

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(testFixtures(project(":core:domain")))
}
