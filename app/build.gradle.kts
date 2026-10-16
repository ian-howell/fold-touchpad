plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.ian.foldtouchpad"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ian.foldtouchpad"
        // Targets current Android only (Galaxy Z Fold 8 ships Android 16).
        minSdk = 36
        targetSdk = 36
        // CI sets BUILD_NUMBER so every release installs as an update.
        val build = System.getenv("BUILD_NUMBER")?.toInt() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "release"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}
