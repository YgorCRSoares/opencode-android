# OpenCode Terminal - Android

A fully functional OpenCode TUI (Text User Interface) running on Android via Termux-like environment.

## 📱 Status

- **App Version**: `v1.0.3-rc3` (fork `guysoft/opencode-termux`, base `2.0.0-android-termux.2`)
- **Runtime**: `v1.0.3-rc3` (auto-detected via `scripts/fetch-runtime.sh`)
- **Build**: Release (signed with debug keystroke, `install -r` safe, no data loss)
- **Compatibility Warning**: **Removed** — app is no longer flagged as "debuggable"
- **App Icon**: Official OpenCode "O" logo (anel quadrado), 5 mipmap densities
- **TUI Footer**: `"2.0.0-android-termux.2"` hidden via `mini.footer: "hide"` config

## ✅ Features Implemented

### Interface
- **TUI fully functional**: OpenCode terminal interface running natively on Android
- **Keyboard row**: Extra row above IME with `Esc Ctrl Alt Modo ← ↑ ↓ →` (glued to keyboard, drops to footer when keyboard closes)
- **Mode cycling**: `Shift+Tab` replaced by single `Modo` button sending `CSI Z` (shift+tab) to cycle opencode modes
- **No compatibility dialog**: App opens directly into TUI without the "16 KB page alignment / debuggable" warning

### Compatibility Warning Removal
- **Root cause**: Android flag `DEBUGGABLE` (build debug) + 16 KB ELF misalignment advisory
- **Fix**: Release build signed with the same debug keystore → `DEBUGGABLE` flag disappears, `install -r` works without data loss (certificate digest `1642e5ea...` matches)
- `build.sh` now runs `gradle assembleRelease`
- `app/build.gradle.kts`: `signingConfig = signingConfigs.getByName("debug")`
- `vendor/libtermexec/src/main/jni/Application.mk`: `APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`

### Runtime Updates
- **`scripts/fetch-runtime.sh`**: Auto-detects latest release from fork feed
  - Lists `releases?per_page=30`, filters `opencode2-*-android-aarch64.zip`, sorts by `published_at` DESC
  - Correctly resolves `v1.0.3-rc3` (all `v1.0.x` are prereleases; `releases/latest` returns stale `v0.2.1`)
  - `--check` mode: side-effect-free, reports "já está na última versão possível (v1.0.3-rc3)"
  - Creates stamp file `runtime-embedded.sha` for cache-based no-op
  - `RUNTIME_MARKER_VERSION` bump only after successful repack
  - 4 bugs fixed from initial failed attempt (vars used before definition, broken Python f-string, undefined `MAIN_ACTIVITY`, wrong SHA comparison)
  - `shellcheck` clean
- Commit: `d746cae`

### Backup/Recovery
- **Commit chain**: Each verified working state is committed + tagged + APK backup in `~/backups/`
- Current backup states: `backup-funcionando-20261030`, `backup-20261030-links-copy-ime`, `backup-perfeito-20261030`, `backup-modo-20261030`, `backup-20261031-footer-fix`
- APK pattern: `opencode-terminal-YYYYMMDD-*.apk`

### App Behavior
- Opens directly into TUI (no setup screens mandatory)
- Links open in default browser
- Copy selection + `COPIAR` bar works
- Row glued above IME / at footer when keyboard closes
- `Modo` button cycles modes
- `Ctrl+P` commands palette accessible

## 🛠 Build & Install

```bash
# Build release (assembles release, signs with debug keystore)
cd /home/HyprKlavier/opencode-android
./build.sh

# Install release APK (preserves data via install -r)
adb install -r app/build/outputs/apk/release/app-release.apk

# Check for latest runtime
bash scripts/fetch-runtime.sh  # reports "já está na última versão possível (v1.0.3-rc3)"
```

## 📂 Directory Structure

```
/opencode-android/
├── app/                        # Android Studio project
│   ├── build.gradle.kts        # Release buildConfig, signingConfig
│   ├── src/main/               # Source files
│   │   ├── AndroidManifest.xml # android:icon + theme
│   │   ├── java/               # EnvBuilder, RuntimeInstaller (footer config)
│   │   ├── res/mipmap-*/       # 5 icon densities (the "O" logo)
│   │   └── assets/runtime-arm64.zip  # Runtime bundle
│   └── build/                  # Generated outputs
├── scripts/
│   └── fetch-runtime.sh        # Auto-latest runtime updater
├── vendor/                     # Vendored dependencies
├── backups/                    # Rollback APKs + git tags
├── .config/opencode/           # TUI config (mini.footer: hide)
├── build.sh                    # Build script (now assembleRelease)
├── README.md                   # This file
└── git/                        # Git repo with commit history
```

## 📦 Release APK

- **Path**: `app/build/outputs/apk/release/app-release.apk`
- **Size**: ~150 MB
- **Signature**: Debug keystore (`~/.config/.android/debug.keystore`), digest `1642e5ea22470cda023708eda240f72ba02164f3cda1cc23d884ee78add657f4`
- **Install**: `adb install -r` — safe over debug build, no data loss

## 🔄 Update Cycle

One command cycle to keep the app at the newest possible version:

```bash
bash scripts/fetch-runtime.sh    # auto-detect + verify runtime version
./build.sh                       # assembleRelease + sign
adb install -r app/build/outputs/apk/release/app-release.apk  # install/update
```

## 🛡 Known States (Git Tags)

| Tag | Description |
|-----|-------------|
| `backup-funcionando-20261030` | Initial verified state |
| `backup-20261030-links-copy-ime` | Links + copy/COPIAR bar + row above IME |
| `backup-perfeito-20261030` | All features working (TUI, keyboard, etc.) |
| `backup-modo-20261030` | `Modo` button + shift+tab replacement |
| `backup-20261031-footer-fix` | **Current**: footer hidden + icon O + release build |
| `backup-20261030-links-copy-ime` | Links + copy functionality |

## 📄 Licença

Este projeto é mantido por HyprKlavier. O ícone oficial usa o SVG do [anomalyco/opencode](https://github.com/anomalyco/opencode).
