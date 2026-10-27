plugins {
    id("com.android.application")
}

android {
    namespace = "dev.opencode.terminal"
    compileSdk = 36
    ndkVersion = "28.1.13356709"

    defaultConfig {
        applicationId = "dev.opencode.terminal"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Vendored jackpal libtermexec (PTY): Java API + JNI C sources.
    // See vendor/libtermexec/VENDORED.md.
    sourceSets {
        getByName("main") {
            java.srcDir(rootProject.file("vendor/libtermexec/src/main/java"))
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = rootProject.file("vendor/libtermexec/src/main/jni/Android.mk")
        }
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
