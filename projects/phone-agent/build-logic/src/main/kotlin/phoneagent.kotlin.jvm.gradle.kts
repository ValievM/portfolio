import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
