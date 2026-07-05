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

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug key by default; the watch app is distributed standalone.
            signingConfig = signingConfigs.getByName("debug")
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
