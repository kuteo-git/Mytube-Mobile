import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iosArm64 is a real iPhone; iosSimulatorArm64 is the simulator on an Apple
    // Silicon Mac. iosX64 — the Intel simulator — is deliberately absent:
    // Compose Multiplatform 1.12.0's `components-resources` does not publish for
    // it, so including it fails resolution for a target no machine here has.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // A JVM target for tests only — no desktop app is shipped from it.
    //
    // Every other target needs hardware to run a test on: connectedAndroidTest
    // wants an emulator, iosSimulatorArm64Test wants a simulator runtime that is
    // several gigabytes. jvmTest runs in seconds with nothing attached, which is
    // the difference between UI tests that run on every edit and UI tests that
    // run whenever somebody remembers to plug a phone in.
    jvm()

    sourceSets {
        commonMain.dependencies {
            // The system back gesture, in common code.
            //
            // A separate artifact rather than part of `compose.ui`: it is in the
            // cache because something transitively resolves it, and it is not on
            // the classpath until it is asked for by name. Without it the only
            // BackHandler is Android's, in `androidx.activity`, which would put
            // navigation behind an `expect/actual` for no reason — the decision
            // about what "back" means is the same on both platforms.
            implementation(libs.compose.backhandler)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // @Preview in common code. The annotation is JetBrains' own, not
            // androidx's: androidx.compose.ui.tooling.preview.Preview is Android
            // only, and a preview that cannot be written in commonMain would
            // mean previews only for half the screens.
            implementation(compose.components.uiToolingPreview)
            // compose.components.resources is not here yet: its iOS klib failed to
            // resolve and nothing uses it. Added back with the first bundled asset.

            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.okhttp)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.session)
            implementation(libs.media3.ui)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.mytube.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mytube.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
