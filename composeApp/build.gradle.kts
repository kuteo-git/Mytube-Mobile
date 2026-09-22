import java.util.Properties
import org.gradle.api.tasks.PathSensitivity
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
            implementation(libs.calf.ui)
            implementation(libs.backdrop)
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

/**
 * The release key, or nothing.
 *
 * `keystore.properties` is gitignored and names a file that lives beside the
 * toolchain on Data2 — §9's rule, applied to the one secret this project has.
 * A key in the repository is a key anybody who clones it can sign with.
 *
 * **Absent is a supported state.** Another machine, or a fresh clone, still
 * builds: the release APK simply comes out unsigned rather than the build
 * failing on a file that was never meant to travel. An unsigned APK cannot be
 * installed, which is the honest outcome — the alternative is a build that
 * silently signs with the debug key and produces something that installs and
 * says `release` on it.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The guards read source files at **run** time, so Gradle cannot see what they
// depend on.
//
// `ArchitectureGuardTest`, `UntranslatedGuardTest`, `PressGuardTest`,
// `ScrollRoomGuardTest` and `SubtitleGuardTest` all scan the tree with
// `File(...).readText()`. None of that is a declared input, so a change to a
// file the guard is *about* leaves the test task up to date and the guard does
// not run — measured: flipping `rendersSubtitles` back to the value that shipped
// the bug left `jvmTest` reporting success, and the same check failed the moment
// it was forced to rerun.
//
// This is worse for `androidMain`, which `jvmTest` does not compile, so nothing
// else invalidates the task either. Declaring the source tree as an input is
// what makes a guard a guard rather than a test that sometimes happens to run.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("guardedSources")
}

android {
    namespace = "com.mytube.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mytube.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // **One number per release, and all four say it.**
        //
        // These had drifted apart: the newest tag was `v0.1.1`, this said
        // `0.1.0`, `versionCode` had never left 1, and the iOS plist said `1.0`
        // and `1`. Nothing shows the number in the app, so nothing caught it —
        // and it cost a round trip: with `CFBundleShortVersionString` stuck at
        // 1.0 there was no way to tell which build was on the phone, and a fix
        // measured on the simulator was reported as still broken because the
        // running binary was the previous one.
        //
        // `versionCode` is the release count and has to rise for Android to
        // accept an install; `versionName` is the tag without its `v`.
        versionCode = 2
        versionName = "0.1.2"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // **Not minified, and that is a decision rather than a default.**
            //
            // R8 strips what it cannot see referenced, and this app reaches for
            // three things it cannot see: Ktor's serializers, Media3's session
            // service through the manifest, and every `expect/actual` the
            // platform resolves. Each one fails at *runtime*, on the build
            // nobody tests as hard as the debug one. The app is a household
            // client on the house wifi — nothing here is paying for the few
            // megabytes shrinking would save, and §8's rule is that nothing is
            // called done because it compiled.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
