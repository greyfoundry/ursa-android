buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9.x ships built-in Kotlin but bundles an older KGP; raise it so the
        // Compose compiler plugin (2.4.10) matches the built-in Kotlin compiler.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
