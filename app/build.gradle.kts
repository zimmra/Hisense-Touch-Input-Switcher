import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing config is read from keystore.properties at the repo root (committed on purpose,
// see the comment in that file).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.zimindustries.inputswitcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zimindustries.inputswitcher"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "keystore/input-switcher-release.jks"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Tiny app with no third-party code: skip R8 so there is nothing to keep/obfuscate.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug builds share the release key so a debug install can be upgraded by a release
            // build (and vice versa) without uninstalling first.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

// Produces app/build/outputs/apk/<variant>/input-switcher-<variant>.apk
base {
    archivesName.set("input-switcher")
}

dependencies {
    // Intentionally empty: plain framework widgets + RemoteViews. No AndroidX, no Compose, no Play.
}
