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

    packaging {
        jniLibs {
            // O runtime é executado via execve; o Android só permite isso no
            // diretório nativo extraído do APK, não em filesDir (W^X).
            useLegacyPackaging = true
            keepDebugSymbols += listOf(
                "**/libopencode2.so",
                "**/librg.so",
                "**/libopentui.so",
                "**/libpcre2-8.so"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideload pessoal: assina release com a MESMA chave de debug para
            // permitir `install -r` por cima do build debug sem perder dados.
            // O efeito desejado é sair de DEBUGGABLE (remove o aviso de
            // compatibilidade de 16 KB que o Android só mostra a apps depuráveis).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // Terminal VT100/xterm vendorizado (jackpal) — ver vendor/emulatorview/VENDORED.md.
    implementation(project(":emulatorview"))

// Terminal emulator and view vendorized from Termux (termux/termux-app)
// See vendor/terminal-emulator/VENDORED.md and vendor/terminal-view/VENDORED.md
implementation(project(":terminal-emulator"))
implementation(project(":terminal-view"))
    testImplementation("junit:junit:4.13.2")
}
