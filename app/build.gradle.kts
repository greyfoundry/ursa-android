plugins {
    alias(libs.plugins.android.application)
    // AGP 9+ has built-in Kotlin; do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.astoris.ursa"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.astoris.ursa"
        minSdk = 26
        targetSdk = 36
        // Literal versionName/versionCode so F-Droid's parser can read them for
        // auto-update (it cannot evaluate variables or arithmetic). release-please
        // updates versionName via the marker; bump versionCode per release by hand
        // (major*10000 + minor*100 + patch).
        versionCode = 10200
        versionName = "1.2.0" // x-release-please-version
    }

    // Release signing: uses the keystore from CI secrets when present, otherwise
    // falls back to the debug key so local `assembleRelease` still produces an
    // installable APK. See docs/infrastructure and the release-please workflow.
    signingConfigs {
        create("release") {
            System.getenv("ANDROID_KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("ANDROID_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // F-Droid rejects the AGP "Dependency metadata" signing block that Google adds to
    // release APKs by default; omit it so the build passes F-Droid's APK scanner.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Keep prebuilt native libs (e.g. datastore's libdatastore_shared_counter.so)
    // unstripped so the APK is byte-for-byte reproducible: AGP's symbol stripping is
    // not deterministic across build hosts, which breaks F-Droid's reproducible build.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    // push: UnifiedPush connector (FOSS, no Firebase). It pulls the JVM `tink`
    // for web-push crypto; we already ship `tink-android` (same classes), so drop
    // the JVM variant to avoid duplicate-class build failures.
    implementation(libs.unifiedpush.connector) {
        exclude(group = "com.google.crypto.tink", module = "tink")
    }

    // biometric / device-credential app lock (FragmentActivity is required by BiometricPrompt)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    // background TLS-expiry reminder
    implementation(libs.androidx.work.runtime)

    // home-screen widget (Jetpack Glance)
    implementation(libs.androidx.glance.appwidget)

    // networking: Socket.IO (live monitors) + Ktor (public status-page REST)
    implementation(libs.socket.io.client) {
        exclude(group = "org.json", module = "json") // Android ships its own org.json
    }
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
