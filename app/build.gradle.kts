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
        versionCode = 1
        versionName = "0.1.0" // x-release-please-version
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
