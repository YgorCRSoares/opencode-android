# VENDORED: emulatorview (terminal VT100/xterm — emulação, render, IME)

## Upstream

- **Repository:** https://github.com/jackpal/Android-Terminal-Emulator
- **Commit:** `35188f8a8b57989a4a4ec9485e11187b46be26d9` (2020-10-20, "Update Readme."),
  cloned with `--depth 1` on 2026-10-27 — mesmo commit do `vendor/libtermexec/`.
- **License:** Apache License 2.0 — full text in `vendor/LICENSE-jackpal.txt`
  (shared with libtermexec), attribution in `vendor/NOTICE`.

## What was copied

| Upstream path | Vendored path |
|---|---|
| `emulatorview/src/main/java/jackpal/androidterm/emulatorview/**` (29 arquivos) | `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/**` |
| `emulatorview/src/main/res/drawable/atari_small.png` | idem em `vendor/emulatorview/src/main/res/` |
| `emulatorview/src/main/res/drawable-nodpi/atari_small_nodpi.png` | idem em `vendor/emulatorview/src/main/res/` |

Added by this repo (not upstream): `vendor/emulatorview/build.gradle.kts`,
`vendor/emulatorview/VENDORED.md`.

Deliberately **not** copied:

- `emulatorview/build.gradle` — AGP 1.0 era (`compileSdkVersion 22`,
  `buildToolsVersion "22.0.1"`), unusable with AGP 8.11; replaced by the
  `build.gradle.kts` above.
- `emulatorview/src/main/AndroidManifest.xml` — só declara `package`
  (ilegal sob AGP 8; o namespace vem do Gradle) e `uses-sdk` antigo; nada de
  componentes/permissions. Mesma decisão da Task 5 para o manifest do libtermexec.
- `emulatorview/.gitignore` — não embarcado.

## Source modifications vs upstream

1. **`PaintRenderer.java`** — `android.util.FloatMath` (removido do SDK após
   API 22; upstream compilava com `compileSdkVersion 22`) → `java.lang.Math`:
   - removido `import android.util.FloatMath;`
   - `FloatMath.ceil(x)` → `Math.ceil(x)` nas duas chamadas (o resultado já era
     convertido para `int`; arredondamento idêntico).

2. **`EmulatorView.java`** — no modo cooked IME, o `inputType` agora inclui
   `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS`
   (além de `TYPE_CLASS_TEXT`). Motivo: o Gboard em `TYPE_NULL` entrega letras
   como key events, e o modificador Ctrl one-shot (`mControlKey`) só é aplicado
   no caminho `commitText` (`mapControlChar`); com a variação de senha o
   teclado usa commitText sem autocorreção/sugestões. `TermController` chama
   `setUseCookedIME(true)`.

Todos os demais arquivos de `src/main/java` e `src/main/res` são byte-idênticos
ao upstream commit `35188f8a` (verificado com `diff -r`).

## Build wiring (em `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts`)

```kotlin
// settings.gradle.kts
include(":emulatorview")
project(":emulatorview").projectDir = file("vendor/emulatorview")

// build.gradle.kts (raiz) — plugin declarado
id("com.android.library") version "8.11.1" apply false

// app/build.gradle.kts
implementation(project(":emulatorview"))
```

`namespace = "jackpal.androidterm.emulatorview"`, `compileSdk = 36`, `minSdk = 28`,
sem Kotlin. As duas drawables exigem o plugin de library (gera a classe `R`);
`Bitmap4x8FontRenderer` referencia `R.drawable.atari_small{,_nodpi}`.

## API do vendor (lida nas fontes — consumida pela Task 6)

**`TermSession`** — o construtor do plano estava errado; a forma real é:

- `TermSession()` e `TermSession(boolean exitOnEOF)` — **não existe**
  `TermSession(InputStream, OutputStream)`.
- Streams são injetados depois: `setTermIn(InputStream)` / `setTermOut(OutputStream)`.
  O upstream (`ShellTermSession`) usa exatamente
  `setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(ptmxFd))` e
  `setTermOut(new ParcelFileDescriptor.AutoCloseOutputStream(ptmxFd))` sobre o
  **mesmo** `ptmx` — é o padrão adotado aqui.
- As threads de leitura/escrita só arrancam em
  `initializeEmulator(cols, rows)`, chamado pelo `EmulatorView` quando a view
  é medida — não é preciso arrancá-las manualmente.
- `finish()` fecha as streams, encerra a thread de escrita e **não** apaga a
  tela principal (`TerminalEmulator.finish()` só descarta o buffer alternativo).

**`EmulatorView`** — `extends View implements GestureDetector.OnGestureListener`:

- Construtor programático real: `EmulatorView(Context, TermSession, DisplayMetrics)`
  (faz `attachSession` + `setDensity` internamente). Não há `EmulatorView(Context)`.
  Alternativa: `EmulatorView(Context, AttributeSet)` + `attachSession(...)`.
- Ciclo de vida: `onResume()` / `onPause()` (blink do cursor, key listener) —
  repassados pelo `MainActivity`.
- `setFocusable(true)` + `setFocusableInTouchMode(true)` são feitos em
  `attachSession`; a view é `onCheckIsTextEditor()` (IME).
- `?1049h/?1049l` trocam `mScreen` entre `mAltBuffer` e `mMainBuffer`
  **sem** save/restore de cursor (lido em `TerminalEmulator.doEscLSBQuest`).

**Fim de sessão:** `TermExec.waitFor(pid)` bloqueia — chamado numa thread de
watcher própria (mesmo padrão do upstream `ShellTermSession`), que faz post no
main looper. Ver `TermController.java`.

## Verificação

`./build.sh` → `BUILD SUCCESSFUL` (Task 6); APK debug renderiza terminal no AVD
`oc_test` (evidências em `docs/evidence/task6-*.png`).
