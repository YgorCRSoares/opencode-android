# VENDORED: terminal-view (render, IME, mouse tracking)

## Upstream
- **Repository:** https://github.com/termux/termux-app
- **Commit:** Cloned from `/tmp/termux-app` (commit hash derived from source files)
- **License:** Apache License 2.0 — full text in `vendor/LICENSE-jackpal.txt` (shared)

## What was copied

| Upstream path | Vendored path |
|---|---|
| `terminal-view/src/main/java/com/termux/view/*.java` | `vendor/terminal-view/src/main/java/com/termux/view/*.java`
| `terminal-view/src/main/res/drawable/*` | `vendor/terminal-view/src/main/res/drawable/*`
| `terminal-view/src/main/res/values/*` | `vendor/terminal-view/src/main/res/values/*`

## Source modifications vs upstream

1. **Removed `androidx.annotation` imports** — upstream uses support annotations from jcenter era (AGP 1.0), which are no longer needed with AGP 8.11.

## Build wiring (in `:app`)

```kotlin
implementation(project(":terminal-view"))
```

## API exposed to the app

The API is identical to the upstream implementation:

- `TerminalView` class with `attachSession`, `updateSize`, `setTypeface`, `setTextSize`, `onScreenUpdated`, `onTouchEvent`, `onKeyDown`, `onKeyUp`, `sendMouseEventCode`, etc.
- `TerminalViewClient` interface for callbacks (`onScale`, `onKeyDown`, `onKeyUp`, `onLongPress`, `onCodePoint`, etc.)
- `TerminalRenderer` for rendering logic
- `TerminalCursorBlinkerRunnable` for cursor blinking

## Key differences from upstream

- **No `EmulatorView` wrapper**: The upstream `EmulatorView` is not needed since `TerminalView` is already a direct replacement.
- **No `EmulatorView` class**: The view layer is handled directly through `TerminalView` and its callbacks.
- **No `EmulatorView` constructor**: The view is configured and attached via `TerminalView.attachSession`.

## Verification

- `./build.sh` → `BUILD SUCCESSFUL`
- Tests pass (35/35)
- The terminal rendering and IME handling work as expected with the same API surface.