pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "opencode-android"
include(":app")
// Emulatorview vendorizado (jackpal) — ver vendor/emulatorview/VENDORED.md.
include(":emulatorview")
project(":emulatorview").projectDir = file("vendor/emulatorview")

include(":terminal-emulator")
project(":terminal-emulator").projectDir = file("vendor/terminal-emulator")

include(":terminal-view")
project(":terminal-view").projectDir = file("vendor/terminal-view")
