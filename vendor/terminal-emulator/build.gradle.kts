plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        
        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector")
            }
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // implementation("androidx.annotation:annotation:1.9.0")
    testImplementation("junit:junit:4.13.2")
}