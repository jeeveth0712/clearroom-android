import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Every APK this project builds must stay under 95 MB (95,000,000 bytes, so also under 95 MB as Windows counts). */
val maxApkBytes = 95L * 1_000_000

android {
    namespace = "com.clearroom.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.clearroom.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Phones only: MediaPipe's native library is 10–21 MB per processor type, and the x86 ones
            // (emulators, Intel Chromebooks) alone added 38 MB. 64- and 32-bit ARM cover Android phones.
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
        // On-phone tests install, run and then uninstall the app under test. A separate application
        // id means that never touches the copy the user measures with, or the rooms saved in it.
        create("uiTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            matchingFallbacks += listOf("debug")
        }
    }
    testBuildType = "uiTest"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // MediaPipe maps the YAMNet model straight out of the APK, which needs it stored uncompressed.
        noCompress += "tflite"
    }
    lint {
        // Phones only (ndk.abiFilters): Intel Chromebooks run the ARM libraries through translation.
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mediapipe.tasks.audio)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // The host activity for Compose UI tests must also be in the build type the phone tests run on.
    "uiTestImplementation"(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/** Fails the build when a packaged APK is over [maxBytes]. */
abstract class ApkSizeCheck : DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val artifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val maxBytes: Property<Long>

    @TaskAction
    fun check() {
        val built = artifactsLoader.get().load(apkFolder.get()) ?: throw GradleException("no APK found in ${apkFolder.get()}")
        built.elements.forEach { apk ->
            val bytes = File(apk.outputFile).length()
            val summary = "%s is %.1f MB; the cap is %.0f MB".format(File(apk.outputFile).name, bytes / 1e6, maxBytes.get() / 1e6)
            if (bytes > maxBytes.get()) throw GradleException("APK too big: $summary")
            logger.lifecycle(summary)
        }
    }
}

// The size check runs after every packaging (assemble, install, the phone tests) and with `check`,
// so no build that grows past the cap goes unnoticed.
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val sizeCheck = tasks.register<ApkSizeCheck>("check${name}ApkSize") {
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            artifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            maxBytes.set(maxApkBytes)
        }
        tasks.matching { it.name == "package$name" }.configureEach { finalizedBy(sizeCheck) }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(sizeCheck) }
    }
}