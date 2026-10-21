plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.textexpander"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.textexpander"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "3.9"
    }

    // On GitHub the workflow provides a fixed key (kept in the Actions cache, never in the repo).
    // Local builds fall back to the debug key.
    val keystore = System.getenv("FLOWKIT_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("FLOWKIT_PASS")
            keyAlias = "flowkit"
            keyPassword = System.getenv("FLOWKIT_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
