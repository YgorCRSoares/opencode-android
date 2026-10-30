plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"
    compileSdk = 36

    dependencies {
        // implementation("androidx.annotation:annotation:1.9.0")
        api(project(":terminal-emulator"))
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}