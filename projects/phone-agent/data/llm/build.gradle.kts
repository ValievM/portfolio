plugins {
    id("phoneagent.kotlin.jvm")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(testFixtures(project(":core:domain")))
}
