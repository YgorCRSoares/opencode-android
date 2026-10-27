plugins {
    id("com.android.library")
}

// Módulo vendorizado de jackpal/Android-Terminal-Emulator (ver VENDORED.md).
// O build.gradle Groovy do upstream (AGP 1.0, compileSdk 22, jcenter era) foi
// substituído por este arquivo; o restante do módulo é idêntico ao upstream.
android {
    namespace = "jackpal.androidterm.emulatorview"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
