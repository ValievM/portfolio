plugins {
    alias(libs.plugins.spotless)
}

// The formatter must not even walk the build folders: KSP rewrites generated files there while
// `check` runs in parallel, and a file that vanishes mid-walk made spotless fail at random.
fun sources(pattern: String) =
    fileTree(rootDir) {
        include(pattern)
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }

spotless {
    kotlin {
        target(sources("**/*.kt"))
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(
                listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"),
            )
    }
    kotlinGradle {
        target(sources("**/*.gradle.kts"))
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("spotlessCheck"))
    }
}
