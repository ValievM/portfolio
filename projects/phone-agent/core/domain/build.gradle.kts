plugins {
    id("phoneagent.kotlin.jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    // Contract tests live in testFixtures so every implementation of a port (the fake and the real one) can run them.
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
