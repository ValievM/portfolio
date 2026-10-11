plugins {
    `kotlin-dsl`
}

group = "com.example.phoneagent.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.2.21")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.2.21")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
}
