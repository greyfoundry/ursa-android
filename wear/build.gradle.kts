plugins {
    alias(libs.plugins.android.application)
    // AGP 9+ has built-in Kotlin; do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.astoris.ursa.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.astoris.ursa"
        // Wear OS 3+ (the modern Tiles API baseline).
        minSdk = 30
        targetSdk = 36
        val appVersion = "1.0.0" // x-release-please-version
        versionName = appVersion
        val (maj, min, pat) = appVersion.split(".", "-").take(3)
            .map { it.filter(Char::isDigit).ifEmpty { "0" }.toInt() }
        versionCode = maj * 10000 + min * 100 + pat
    }

    // Release signing mirrors the app module: the keystore from CI secrets when present,
    // otherwise the debug key so local `assembleRelease` still produces an installable APK.
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
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Wear Tiles + ProtoLayout (all FOSS androidx, no Google Play Services).
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.guava) // ListenableFuture for TileService responses

    // networking: Ktor to poll a public Kuma status page (no auth, no GMS)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
