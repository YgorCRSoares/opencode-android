plugins {
    id("com.android.application")
}

android {
    namespace = "dev.opencode.verify"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.opencode.verify"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
