# OpenCode Terminal — plano de experiência mobile

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** aproximar o APK da TUI real do OpenCode da experiência do PC, entregando
cada melhoria como uma subtarefa verificável no Galaxy S26+.

**Architecture:** manter o `EmulatorView` + PTY + runtime atuais e evoluir a camada
de apresentação/entrada: fonte embarcada no renderer, correção UTF-8 na sessão,
mouse no emulatorview, tema imersivo, escala calculada e teclado próprio que envia
texto/teclas ao `TermSession`. Nenhum shell genérico volta a ser o caminho principal.

**Tech Stack:** Java 17, AGP 8.11.1, Gradle 8.14.3, JUnit 4, emulatorview vendorizado,
Adwaita Mono (OFL 1.1), Android 16/API 36 no S26+.

**Design:** `docs/superpowers/specs/2026-10-28-opencode-terminal-experience-design.md`

---

## Regras de execução

- Uma subtarefa por vez; não iniciar a próxima sem build/teste e screenshot no S26+.
- Nunca solicitar, imprimir ou commitar API key. Testes de LLM usam o modelo grátis
  já validado; nenhuma key do usuário entra em log ou comando.
- Não adicionar log de PTY/transcript. Logs de dimensão/plano são permitidos.
- Rodar `shellcheck` em scripts alterados antes de executar.
- Build: `./build.sh` (ou `./gradlew assembleDebug`); testes: `./gradlew testDebugUnitTest`.
- Instalação/validação: `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
  `adb shell am force-stop dev.opencode.terminal`, iniciar e capturar screenshot.
- Commits pequenos, um por subtarefa concluída.

## Baseline atual

- [x] Runtime real abre no S26+ (`plan=RUNTIME reason=ok`).
- [x] Um turno LLM real concluído sem key do usuário.
- [x] Evidências atuais em `docs/evidence/phone-{current-home,current-keyboard,missing-glyphs,session-ok}.png`.
- [x] Fonte do S26+ inspecionada: sem block/braille/`■`/`⬝`.
- [x] TUI v2.0.0 tem logo; terminal com ~72 colunas e espaço suficiente.
- [x] `TermSession.write(int)` confirmado como causa dos acentos perdidos.
- [x] `mMouseTracking` confirmado como `false`; nenhum `setMouseTracking(true)`.

---

## Task 1 — S1: fonte e glifos

**Files:**
- Create: `app/src/main/assets/fonts/AdwaitaMono-Regular.ttf`
- Create: `app/src/main/assets/fonts/LICENSE-Adwaita.txt`
- Create: `app/src/main/java/dev/opencode/terminal/TerminalTypeface.java`
- Modify: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/PaintRenderer.java`
- Modify: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java`

**Interfaces:**
- `TerminalTypeface.load(Context): Typeface`
- `EmulatorView.setTypeface(Typeface): void`
- `PaintRenderer(int fontSize, ColorScheme scheme, Typeface typeface)`

- [ ] Copy `AdwaitaMono-Regular.ttf` and the OFL license into assets.
- [ ] Add `TerminalTypeface` with `Typeface.createFromAsset`; on failure return
  `Typeface.MONOSPACE` and log only a warning.
- [ ] Add `EmulatorView.mTypeface` (default `Typeface.MONOSPACE`), public
  `setTypeface`, and pass it to `new PaintRenderer(...)` in `updateText()`.
- [ ] Add the typeface overload to `PaintRenderer`; keep the old constructor
  delegating with `Typeface.MONOSPACE`.
- [ ] In `TermController.start`, after adding the `EmulatorView` to the container,
  call `mView.setTypeface(TerminalTypeface.load(mActivity))`.
- [ ] Build, run unit tests, install, restart and screenshot the home.
- [ ] Verify: logo visible, spinner/status with `■`/`⬝`/Braille, no tofu.
- [ ] Commit S1 with the screenshot evidence.

**Acceptance:** home do S26+ mostra o logo; spinner/status sem quadrados tofu.

---

## Task 2 — S2: UTF-8 e acentos

**Files:**
- Create: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/Utf8.java`
- Modify: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/TermSession.java`
- Create: `app/src/test/java/dev/opencode/terminal/Utf8Test.java`
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java` (sendText)

**Interfaces:**
- `public static byte[] Utf8.encode(int codePoint) throws IllegalArgumentException`
- `TermController.sendText(String)` → `TermSession.write(String)`

- [ ] Add `Utf8.encode` using `Character.toChars` + `StandardCharsets.UTF_8`.
- [ ] Change `TermSession.write(int)` to call `Utf8.encode` for non-ASCII and
  `write(bytes, 0, bytes.length)`; keep the ASCII fast path.
- [ ] Add JUnit tests: `A`, `é`, `ç`, `ã`, `ô`, emoji, invalid code point.
- [ ] Add `TermController.sendText` for the future internal keyboard.
- [ ] Run `./gradlew testDebugUnitTest`.
- [ ] Install and type `ação`, `coração`, `ç`, `ã`, `é`, `ô` using Gboard; screenshot.
- [ ] Commit S2.

**Acceptance:** os seis exemplos aparecem exatamente no prompt da TUI; testes verdes.

---

## Task 3 — S3: mouse/toque nas abas

**Files:**
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java`
- Optional: `vendor/.../EmulatorView.java` only if SGR for needed mouse events.

- [ ] Call `mView.setMouseTracking(true)` after creating the view.
- [ ] Restart, tap the “Resposta OK” tab, verify the session switches.
- [ ] Verify scroll/toque doesn't open Gboard.
- [ ] If X10 is insufficient on device, add SGR encoding and retest.
- [ ] Screenshot of switched session; commit S3.

**Acceptance:** tocar na aba troca de sessão; scroll/toque funciona sem barra nativa.

---

## Task 4 — S4: imersão e ActionBar

**Files:**
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/dev/opencode/terminal/MainActivity.java`
- Modify: `app/src/main/java/dev/opencode/terminal/ContentInsets.java`

- [ ] Add `TerminalTheme` (`Theme.Material.Light.NoActionBar`); keep Setup on a
  theme with ActionBar.
- [ ] MainActivity: hide status/navigation with `WindowInsetsController` API 30+;
  fallback for API 28/29; edge swipe reveals.
- [ ] Adjust `ContentInsets` so the terminal has no ActionBar padding and Setup
  remains fully visible; handle display cutout.
- [ ] Verify Setup: BACK does not start the app; form visible; save/skip still work.
- [ ] Screenshot terminal full-screen and Setup; commit S4.

**Acceptance:** sem “OpenCode Terminal”; barras somem/voltam por swipe; Setup intacto.

---

## Task 5 — S5: Build ↔ Plan

**Files:**
- Modify: `app/src/main/java/dev/opencode/terminal/KeyMapper.java`
- Modify: `app/src/main/java/dev/opencode/terminal/ExtraKeysView.java`
- Later: `TerminalKeyboardView` (S7)

- [ ] Add `SHIFT_TAB`/`BUILD_PLAN` mapping to `ESC [ Z`.
- [ ] Add a Build/Plan button in the extra-keys row (and later in the internal keyboard).
- [ ] Verify status changes on device; screenshot before/after.
- [ ] Commit S5.

**Acceptance:** o agente alterna Build ↔ Plan pelo botão.

---

## Task 6 — S6: escala automática

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/TerminalScale.java`
- Create: `app/src/test/java/dev/opencode/terminal/TerminalScaleTest.java`
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- `static int targetColumns(boolean landscape): int`
- `static int fontSizePx(int widthPx, int columns, float charWidthRatio, int minPx, int maxPx)`
- `TermController.applyScale()` (idempotent)

- [ ] Implement pure math with target 80 portrait / 120 landscape, min 8 sp/max 20 sp.
- [ ] Measure char width with the bundled typeface via `Paint`; convert px→dp.
- [ ] Apply after first layout and on size/orientation changes; guard loops.
- [ ] Add `android:configChanges="orientation|screenSize|keyboardHidden"` and
  recalculate without restarting the TUI.
- [ ] Unit-test min/max/rounding/orientation.
- [ ] Verify 80/120 columns, no clipping, PTY `winsize` coherent; screenshot.
- [ ] Commit S6.

**Acceptance:** escala legível e automática; rotação preserva a sessão.

---

## Task 7 — S7: teclado interno

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/TerminalKeyboardView.java`
- Create: `app/src/main/java/dev/opencode/terminal/KeyboardController.java`
- Create: `app/src/main/java/dev/opencode/terminal/KeyboardKey.java`
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java`
- Modify: `app/src/main/java/dev/opencode/terminal/MainActivity.java`
- Modify: `app/src/main/java/dev/opencode/terminal/ExtraKeysView.java` (replace/retire)

### 7.1 Estrutura e letras

- [ ] Create a vertical key view with top control row + QWERTY rows; no Android IME.
- [ ] `setShowSoftInputOnFocus(false)`; `KB` opens Gboard only explicitly.
- [ ] Send letters via `TermController.sendText`; shift/caps; space/backspace/enter.
- [ ] Recalculate terminal height/PTY on show/hide; verify last TUI line visible.

### 7.2 Números, símbolos e acentos

- [ ] Add symbols layer and accent layer; long-press vowels for variants.
- [ ] Ensure `ç`, `ã`, `õ`, `é`, `ê`, `á`, `à`, `â`, `í`, `ó`, `ô`, `ú`, `ü`.
- [ ] Unit-test `KeyboardController` state and emitted strings with a fake sink.

### 7.3 Controles e Build/Plan

- [ ] Esc, Ctrl, Alt, Tab, arrows, `/`, `|`, `-`, Build/Plan.
- [ ] Ctrl/Alt latches; Shift+Tab; avoid sticky modifiers after a key.
- [ ] Add Gboard fallback and a hide-keyboard action.

### 7.4 Aceite

- [ ] Type accents and control sequences on the S26+; screenshot.
- [ ] No text/keystrokes logged; no raw PTY transcript.
- [ ] Commit S7 in increments.

**Acceptance:** é possível usar a TUI inteira sem Gboard; Gboard só por escolha.

---

## Task 8 — S8: regressão e entrega

- [ ] `./gradlew testDebugUnitTest` and `./build.sh`.
- [ ] `shellcheck` on changed scripts.
- [ ] Fresh install/restart with setup done and skipped path.
- [ ] Verify providers/auth remain TUI-owned; no key requested by app.
- [ ] Verify fallback shell still works in AVD x86_64 (no regression).
- [ ] Final screenshots: home with logo, session tabs, Build/Plan, internal keyboard,
  accents, immersive.
- [ ] `git status` clean (except intended evidence), commit final.

---

## Review checklist

- [ ] Each task has a screenshot and a test/verification command.
- [ ] No secret/key in code, logs, docs, screenshots, or git history.
- [ ] Setup BACK behavior preserved.
- [ ] No raw PTY/transcript logging.
- [ ] TUI real remains the main path; fallback only for diagnostics.
- [ ] Font/license included; no network dependency at runtime.
- [ ] Android 15/16 edge-to-edge and cutout handled.
