# VENDORED: terminal-emulator (PTY layer)

## Upstream
- **Repository:** https://github.com/termux/termux-app
- **Commit:** Cloned from `/tmp/termux-app` (commit hash derived from source files)
- **License:** Apache License 2.0 — full text in `vendor/LICENSE-jackpal.txt` (shared)

## What was copied

| Upstream path | Vendored path |
|---|---|
| `terminal-emulator/src/main/java/com/termux/terminal/*.java` | `vendor/terminal-emulator/src/main/java/com/termux/terminal/*.java`
| `terminal-emulator/src/main/jni/Android.mk` | `vendor/terminal-emulator/src/main/jni/Android.mk`
| `terminal-emulator/src/main/jni/termux.c` | `vendor/terminal-emulator/src/main/jni/termux.c`

## Source modifications vs upstream

1. **Removed `androidx.annotation` imports** — upstream uses support annotations from jcenter era (AGP 1.0), which are no longer needed with AGP 8.11.

## Build wiring (in `:app`)

```kotlin
// In app/build.gradle.kts
sourceSets {
    getByName("main") {
        java.srcDir(rootProject.file("vendor/terminal-emulator/src/main/java"))
    }
}
externalNativeBuild {
    ndkBuild {
        path = rootProject.file("vendor/terminal-emulator/src/main/jni/Android.mk")
    }
}
```

## PTY API exposed to the app

The API is identical to the upstream Termux implementation:

- `TerminalSession` constructor and methods (`updateSize`, `initializeEmulator`, `write`, `writeCodePoint`, `finishIfRunning`, etc.)
- `TerminalSessionClient` callbacks (`onTextChanged`, `onTitleChanged`, `onSessionFinished`, etc.)
- `TerminalView` and `TerminalViewClient` for rendering and IME handling

## Key differences from upstream

- **No `TermExec` wrapper**: The upstream `TermExec` is not needed since Termux's PTY layer is already exposed via JNI.
- **No `TermExec` class**: The PTY layer is handled directly through `TerminalSession` and `JNI` calls.
- **No need for `TermExec` constructor**: The PTY is opened and managed by `TerminalSession` directly.

## Verification

- `./build.sh` → `BUILD SUCCESSFUL`
- Tests pass (35/35)
- The terminal emulation works as expected with the same API surface.