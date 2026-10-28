# VENDORED: libtermexec (PTY layer)

## Upstream

- **Repository:** https://github.com/jackpal/Android-Terminal-Emulator
- **Commit:** `35188f8a8b57989a4a4ec9485e11187b46be26d9` (2020-10-20, "Update Readme."),
  cloned with `--depth 1` on 2026-10-27
- **License:** Apache License 2.0 — full text in `vendor/LICENSE-jackpal.txt`,
  attribution in `vendor/NOTICE`

## What was copied

| Upstream path | Vendored path |
|---|---|
| `libtermexec/src/main/java/jackpal/androidterm/TermExec.java` | `vendor/libtermexec/src/main/java/jackpal/androidterm/TermExec.java` |
| `libtermexec/src/main/jni/process.cpp` | `vendor/libtermexec/src/main/jni/process.cpp` |
| `libtermexec/src/main/jni/process.h` | `vendor/libtermexec/src/main/jni/process.h` |
| `LICENSE` | `vendor/LICENSE-jackpal.txt` |

Added by this repo (not upstream): `vendor/libtermexec/src/main/jni/Android.mk`,
`vendor/libtermexec/VENDORED.md`.

Deliberately **not** copied:

- `libtermexec/build.gradle` — AGP 1.0/jcenter era, unusable with AGP 8.11; replaced by
  the wiring in `app/build.gradle.kts` (see below).
- `src/main/aidl/.../v1/ITerminal.aidl` — Binder IPC interface used by upstream's `term`
  app service; not part of PTY spawn (nothing in `TermExec`/`process.*` references it).
- `src/main/AndroidManifest.xml` — no resources/manifest entries needed, and its
  `package` attribute is illegal under AGP 8 (namespace comes from the Gradle build).
- `src/androidTest/`, `proguard-rules.pro`, `.gitignore` — tests/rules not shipped.

## Source modifications vs upstream

`process.h` and `LICENSE` are byte-identical. Two files were touched:

1. **`TermExec.java`** — removed `import android.support.annotation.NonNull` and its
   eight `@NonNull` annotations. Reason: they would pull the EOL
   `com.android.support:support-annotations` artifact (jcenter era) for annotations only.
   **Behavior fix (2026-10-28):** upstream removes `command[0]` and the native
   `createSubprocessInternal` builds `argv` only from the remaining arguments, so a
   one-element command produced `execv(cmd, NULL)` (logcat: `process ... with NULL
   argv: empty string added`) and a multi-element command lost the executable as
   `argv[0]`. The Java side now prepends `cmd` back before calling `createSubprocess`,
   matching the API documented below (`argv[0]` is the executable).
2. **`process.cpp`** — three minimal fixes for NDK r28's clang (default C++17):
   - added `#include <string.h>` (upstream relied on transitive declarations of
     `strcmp`/`memset`/`strerror`);
   - removed `typedef unsigned short char16_t;` (ill-formed: `char16_t` is a builtin
     type in C++11+);
   - `String8::set(const char16_t*)` → `String8::set(const jchar*)` — `jchar` is
     `unsigned short`, exactly what the old typedef meant, and all three call sites
     pass JNI `jchar*` (`GetStringCritical` results).

## Build path: ndkBuild + source-set arrangement (in `:app`)

**Chosen: ndkBuild**, declared in `app/build.gradle.kts`:

```kotlin
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
```

Reasons:

- **ndkBuild vs CMake:** upstream ships no `Android.mk` (AGP 1.0 generated one from the
  removed `ndk { moduleName ... }` block), so the brief allowed either. ndkBuild was
  picked because it needs **no extra SDK component** (CMake requires
  `sdkmanager "cmake;3.22.1"`), it is a single trivial makefile, and it matches the
  module's original ndk-build heritage. `Android.mk` is written by this repo (see its
  header comment); module name `jackpal-termexec2` matches
  `System.loadLibrary("jackpal-termexec2")` in `TermExec`.
- **Source-set arrangement vs separate Gradle library module:** kept everything in
  `:app` (one `java.srcDir` + one `externalNativeBuild` block) — no extra Gradle module
  or root-plugin changes, and it matches the task's file list. Paths must be
  `rootProject.file(...)`: a bare `file()` resolves under `app/`.
- ABI selection comes from the existing `defaultConfig.ndk.abiFilters =
  ["arm64-v8a", "x86_64"]`; `ndkVersion = "28.1.13356709"` was already declared.

Verified: `./build.sh` → `BUILD SUCCESSFUL`, and `libjackpal-termexec2.so` is packaged
under **both** `lib/arm64-v8a/` and `lib/x86_64/` of `app-debug.apk`.

## PTY API exposed to the app (consumed by Task 6)

**Path chosen: vendored API used as-is — no wrapper class.** `TermExec` already gives
Java the master fd (the caller opens `/dev/ptmx` and keeps the descriptor) and the pid
(returned by `start()`), which is exactly what the task asked for.

```java
import jackpal.androidterm.TermExec;
import android.os.ParcelFileDescriptor;
import java.io.File;

// 1. Open the PTY master yourself — TermExec never opens it:
ParcelFileDescriptor ptmx =
        ParcelFileDescriptor.open(new File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE);

// 2. Spawn (must NOT run on the main thread — IllegalStateException otherwise).
//    argv[0] is the executable; the rest are arguments. One-shot instance.
TermExec term = new TermExec("/system/bin/sh", "-i");
// optional env overrides; defaults to System.getenv():
term.environment().put("TERM", "xterm-256color");
int pid = term.start(ptmx);          // returns child pid; master fd = ptmx (caller owns/closes it)

// 3. I/O: read()/write() on ptmx (the master fd) — this is the terminal pipe.

// 4. Lifecycle:
int exitCode = TermExec.waitFor(pid);   // blocks until child exits
TermExec.sendSignal(pid, signal);       // e.g. 2 = SIGINT, 15 = SIGTERM (supports negative pids)
```

Caveats for Task 6:

- **No `cwd` support** — the vendored C uses `execv` with the inherited working
  directory; there is no chdir. The task's optional `Pty.spawn(..., cwd)` wrapper was
  therefore not created (master fd + pid are exposed as-is, per the brief).
- `TermExec.command(List<String>)` is broken upstream (it clears/adds the *argument*
  list, not the command) — use the constructor or `command(String...)`.
- `start()` is single-use: it consumes `argv[0]` from the command list.
- The static initializer loads `libjackpal-termexec2.so` — present for both ABIs.
- Caller closes `ptmx` when the session ends (C sets `FD_CLOEXEC` on it).

## 16 KB page alignment

NDK r28 defaults to 16 KB segment alignment; `llvm-readelf -lW` on the built
arm64 `libjackpal-termexec2.so` shows both `LOAD` segments aligned at `0x4000`
(16384). No alignment warnings were emitted during the build.
