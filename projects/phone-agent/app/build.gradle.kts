@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("phoneagent.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${schemaDir.path}")
}

ksp {
    useKsp2.set(true)
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

android {
    namespace = "com.example.phoneagent"

    defaultConfig {
        applicationId = "com.example.phoneagent"
        versionCode = 1
        versionName = "0.1.0"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        release {
            // R8 code shrinking for stage 5.6; resource shrinking stays off until the minified
            // build is verified to launch on a real device (docs/manual-verification.md).
            isMinifyEnabled = true
            isShrinkResources = false
            // Signed with the local debug key so the minified build can be installed for the
            // manual check; the real release signing is stage 6.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Compose UI tests (screenshot/ and *UiTest) need androidx.activity.ComponentActivity from
// ui-test-manifest, which only the debug variant carries; what they check does not depend on the
// build type.
tasks.withType<Test>().configureEach {
    if (name.contains("Release")) exclude("**/screenshot/**", "**/*UiTest*")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":data:llm"))
    implementation(project(":data:auth"))
    implementation(project(":tools"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.okhttp)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    constraints {
        // markdown-renderer-code asks for highlights 1.1.0, which is compiled for Java 21 and cannot
        // be loaded by the JDK 17 the unit tests run on (this project's toolchain); 1.0.0 is Java 8
        // bytecode with the API the renderer uses. Same reason the renderer stays on 0.39.x.
        implementation(libs.snipme.highlights) {
            version { strictly(libs.versions.snipmeHighlights.get()) }
        }
    }
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(testFixtures(project(":core:domain")))

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
