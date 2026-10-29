# Touch Text Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** permitir selecionar texto em qualquer parte do terminal com toque longo e arrasto, oferecendo uma barra **Copiar/Cancelar** sem quebrar o clique de mouse da TUI.

**Architecture:** manter a seleção dentro do `EmulatorView`. O toque curto continua enviando mouse; o toque longo cria uma seleção linear, o arrasto estende a seleção e pode auto-rolar o transcript; ao soltar, uma `PopupWindow` não focável copia via `ClipboardManagerCompat` somente após confirmação. Nenhuma alteração em `MainActivity`, `TermController`, tema ou teclado.

**Tech Stack:** Java 17, Android SDK 36, JUnit 4, emulatorview vendorizado, AGP 8.11.1, Gradle 8.14.3.

**Spec:** `docs/superpowers/specs/2026-10-28-touch-selection-design.md`

## Global Constraints

- Editar apenas `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`, criar `SelectionRange.java` no mesmo pacote e criar o teste em `app/src/test/java/dev/opencode/terminal/SelectionRangeTest.java`.
- Não editar `MainActivity.java`, `TermController.java`, `styles.xml`, `AndroidManifest.xml`, `TerminalKeyboardView.java` ou `KeyMapper.java`.
- Não adicionar bibliotecas externas.
- Toque curto deve continuar enviando `press/release` de mouse quando `isMouseTrackingActive()` for verdadeiro.
- O clipboard só pode ser alterado depois de tocar em **Copiar**.
- Não registrar em log o texto selecionado nem qualquer credencial.
- O Gboard não deve abrir automaticamente.
- Preservar indentação e quebras de linha; não usar `.trim()` no texto copiado.
- Build: `./build.sh`; testes: `gradle testDebugUnitTest` com `JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21`, `ANDROID_HOME=/home/HyprKlavier/android-sdk` e `PATH` incluindo `~/.local/opt/gradle/bin` e `$ANDROID_HOME/platform-tools`.
- Instalação: `adb install -r app/build/outputs/apk/debug/app-debug.apk`; reinício: `adb shell am force-stop dev.opencode.terminal` e `adb shell am start -n dev.opencode.terminal/.MainActivity`.
- Screenshots em `docs/evidence/`.

## Review Focus

- **Arrasto de baixo para cima atravessando linhas:** deve selecionar o intervalo linear na ordem correta, sem trocar as colunas inicial/final; coberto pelos testes de `SelectionRange` na Task 1.
- **Auto-scroll no topo/rodapé:** deve continuar estendendo a seleção enquanto o dedo fica parado na borda; coberto pelo teste manual da Task 2.
- **TUI atualizando durante a seleção:** o realce não pode copiar texto deslocado; coberto pelo teste manual da Task 2 e pela regressão da Task 4.
- **Toque curto depois de cancelar/copiar:** não pode enviar clique fantasma para a TUI nem abrir o Gboard; coberto pela regressão da Task 4.
- **Barra sobre o teclado nativo:** Copiar/Cancelar devem ficar visíveis e não focáveis, sem redimensionar o terminal; coberto pelo teste manual da Task 3 e pela regressão da Task 4.

---

### Task 1: Normalização pura da seleção linear

**Files:**
- Create: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/SelectionRange.java`
- Create: `app/src/test/java/dev/opencode/terminal/SelectionRangeTest.java`

**Interfaces:**
- Produces: `SelectionRange.fromDrag(int anchorX, int anchorY, int currentX, int currentY)`.
- Produces: campos públicos `int startX`, `int startY`, `int endX`, `int endY` com `startY <= endY` e, na mesma linha, `startX <= endX`.

- [ ] **Step 1: Write the failing test**

```java
package dev.opencode.terminal;

import jackpal.androidterm.emulatorview.SelectionRange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SelectionRangeTest {
    @Test
    public void keepsForwardSelection() {
        SelectionRange r = SelectionRange.fromDrag(2, 5, 8, 5);
        assertEquals(2, r.startX);
        assertEquals(5, r.startY);
        assertEquals(8, r.endX);
        assertEquals(5, r.endY);
    }

    @Test
    public void reversesBackwardSelection() {
        SelectionRange r = SelectionRange.fromDrag(8, 9, 2, 2);
        assertEquals(2, r.startX);
        assertEquals(2, r.startY);
        assertEquals(8, r.endX);
        assertEquals(9, r.endY);
    }

    @Test
    public void normalizesSameRowFromTheRight() {
        SelectionRange r = SelectionRange.fromDrag(9, 4, 3, 4);
        assertEquals(3, r.startX);
        assertEquals(4, r.startY);
        assertEquals(9, r.endX);
        assertEquals(4, r.endY);
    }

    @Test
    public void keepsSingleCell() {
        SelectionRange r = SelectionRange.fromDrag(7, 3, 7, 3);
        assertEquals(7, r.startX);
        assertEquals(3, r.startY);
        assertEquals(7, r.endX);
        assertEquals(3, r.endY);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21
export ANDROID_HOME=/home/HyprKlavier/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:/home/HyprKlavier/.local/opt/gradle/bin:$PATH"
gradle testDebugUnitTest --tests dev.opencode.terminal.SelectionRangeTest --no-daemon
```

Expected: FAIL because `jackpal.androidterm.emulatorview.SelectionRange` does not exist.

- [ ] **Step 3: Implement `SelectionRange`**

Create the immutable helper in the emulatorview package:

```java
public final class SelectionRange {
    public final int startX;
    public final int startY;
    public final int endX;
    public final int endY;

    private SelectionRange(int startX, int startY, int endX, int endY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public static SelectionRange fromDrag(int anchorX, int anchorY,
                                          int currentX, int currentY) {
        if (anchorY < currentY || (anchorY == currentY && anchorX <= currentX)) {
            return new SelectionRange(anchorX, anchorY, currentX, currentY);
        }
        return new SelectionRange(currentX, currentY, anchorX, anchorY);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the same command from Step 2.

Expected: PASS (4 tests).

- [ ] **Step 5: Commit only the new helper and test**

```bash
git add vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/SelectionRange.java \
        app/src/test/java/dev/opencode/terminal/SelectionRangeTest.java
git commit -m "feat: add linear selection range helper"
```

---

### Task 2: Long-press, drag and auto-scroll in `EmulatorView`

**Files:**
- Modify: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`

**Interfaces:**
- Consumes: `SelectionRange.fromDrag(...)` from Task 1.
- Produces: selection behavior while `mIsSelectingText` is true; no new public API.

- [ ] **Step 1: Add the selection state and conversion**

In `EmulatorView`:

- import `SelectionRange`;
- add `private boolean mSelectionDragging;`
- add `private float mLastSelectionX;`
- add `private float mLastSelectionY;`
- add `private int mSelectionScrollDirection;`
- add `private final Runnable mSelectionAutoScroll = ...` (see Step 3).

Add a private method:

```java
private int selectionColumn(float x) {
    return Math.max(0, Math.min(mColumns - 1, (int) (x / mCharacterWidth)));
}

private int selectionRow(float y) {
    int row = (int) ((y + SELECT_TEXT_OFFSET_Y * mScaledDensity) / mCharacterHeight) + mTopRow;
    int min = -mEmulator.getScreen().getActiveTranscriptRows();
    int max = mRows - 1;
    return Math.max(min, Math.min(max, row));
}

private void applySelection(int anchorX, int anchorY, int currentX, int currentY) {
    SelectionRange range = SelectionRange.fromDrag(anchorX, anchorY, currentX, currentY);
    mSelX1 = range.startX;
    mSelY1 = range.startY;
    mSelX2 = range.endX;
    mSelY2 = range.endY;
    invalidate();
}
```

- [ ] **Step 2: Start selection on long press**

Replace `onLongPress` so it no longer calls `showContextMenu()`:

```java
@Override
public void onLongPress(MotionEvent e) {
    if (mIsSelectingText) {
        return;
    }
    int x = selectionColumn(e.getX());
    int y = selectionRow(e.getY());
    mSelXAnchor = x;
    mSelYAnchor = y;
    mSelX1 = x;
    mSelY1 = y;
    mSelX2 = x;
    mSelY2 = y;
    mSelectionDragging = true;
    mLastSelectionX = e.getX();
    mLastSelectionY = e.getY();
    mSelectionScrollDirection = 0;
    toggleSelectingText();
    invalidate();
}
```

- [ ] **Step 3: Extend selection while dragging**

Replace `onTouchEventWhileSelectingText` with this behavior:

- `ACTION_DOWN`: cancel any visible action bar, set anchors, enable dragging, update scroll direction.
- `ACTION_MOVE`: update `mLastSelectionX/Y`, compute current cell, call `applySelection(...)`, update auto-scroll.
- `ACTION_UP`: stop auto-scroll, update endpoint once, set `mSelectionDragging = false`; call `showSelectionActionBar()` if `getSelectedText()` is non-empty; do **not** copy here.
- `ACTION_CANCEL`: stop auto-scroll, hide the action bar, clear selection.
- No default branch should toggle selection off.

Auto-scroll requirements:

- direction `-1` when `mLastSelectionY` is within 1.5 character heights of the terminal top;
- direction `+1` when it is within 1.5 character heights of the terminal bottom;
- otherwise direction `0`;
- every ~50 ms while direction is non-zero and `mSelectionDragging`: move `mTopRow` by the direction, clamp to `[-mEmulator.getScreen().getActiveTranscriptRows(), 0]`, recalculate the endpoint with `selectionRow(mLastSelectionY)`, call `applySelection(...)` and repost;
- `ACTION_UP`/`ACTION_CANCEL` remove callbacks.

- [ ] **Step 4: Keep the existing mouse behavior**

Do not change `onSingleTapUp`, `onScroll`, `onFling`, `isMouseTrackingActive`, or the `mMouseTracking` field. While `mIsSelectingText` is true, `onTouchEvent` must keep routing to `onTouchEventWhileSelectingText`.

- [ ] **Step 5: Build**

Run:

```bash
./build.sh
```

Expected: `BUILD SUCCESSFUL` and `app-debug.apk` generated.

- [ ] **Step 6: Install and verify selection highlight**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop dev.opencode.terminal
adb shell am start -n dev.opencode.terminal/.MainActivity
```

Manually:

1. Long-press a line of text for ~700 ms.
2. Drag up and down; verify the highlight grows and shrinks.
3. Drag past the top/bottom; verify auto-scroll and selection extension.
4. Release; verify the selection remains highlighted for now (the bar comes in Task 3).
5. Single-tap a TUI tab after cancelling; verify it still switches sessions.

Capture a screenshot with a visible selection.

- [ ] **Step 7: Commit the selection interaction**

```bash
git add vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java
git commit -m "feat: add long-press terminal text selection"
```

---

### Task 3: Copy/Cancel bar and clipboard

**Files:**
- Modify: `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`

**Interfaces:**
- Consumes: `getSelectedText()` and selection state from Task 2.
- Produces: private `showSelectionActionBar()`, `hideSelectionActionBar()`, `copySelectedText()`, `clearSelection()`.

- [ ] **Step 1: Build the non-focusable action bar**

Add a `PopupWindow` with a dark horizontal `LinearLayout`:

- **Copiar** button;
- **Cancelar** button;
- `setFocusable(false)`;
- `setOutsideTouchable(true)`;
- no terminal resize;
- positioned above the native keyboard, inside the terminal view bounds;
- dismissed by `hideSelectionActionBar()`.

Use only platform widgets and `GradientDrawable`/`ColorDrawable`; no external libraries.

- [ ] **Step 2: Implement copy and cancel**

`copySelectedText()`:

- reads `String text = getSelectedText();`
- if `text.trim().isEmpty()`, call `clearSelection()` and return;
- writes the untouched `text` to `ClipboardManagerCompatFactory.getManager(getContext().getApplicationContext()).setText(text)`;
- shows `Toast.makeText(getContext(), "Texto copiado", Toast.LENGTH_SHORT).show()`;
- calls `clearSelection()`;
- never logs `text`.

`clearSelection()`:

- hides the action bar;
- stops auto-scroll;
- if `mIsSelectingText` is true, calls `toggleSelectingText()`;
- resets `mSelX1`, `mSelY1`, `mSelX2`, `mSelY2`, `mSelXAnchor`, `mSelYAnchor`;
- calls `invalidate()`.

`Cancelar` calls `clearSelection()` only.

- [ ] **Step 3: Show the bar only after a non-empty release**

Call `showSelectionActionBar()` from `ACTION_UP` only when:

- `mSelectionDragging` was true;
- the selection is not empty after trimming for the emptiness check;
- the view is attached to a window.

Do not show the bar on long-press before dragging, and do not copy on `ACTION_UP`.

- [ ] **Step 4: Build and unit tests**

```bash
./build.sh
export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21
export ANDROID_HOME=/home/HyprKlavier/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:/home/HyprKlavier/.local/opt/gradle/bin:$PATH"
gradle testDebugUnitTest --no-daemon
```

Expected: build succeeds and all unit tests pass.

- [ ] **Step 5: Verify copy/cancel on the S26+**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop dev.opencode.terminal
adb shell am start -n dev.opencode.terminal/.MainActivity
```

Manually:

1. Long-press and drag over multiple lines containing indentation.
2. Release; verify **Copiar/Cancelar** appears above the native keyboard and does not steal focus.
3. Tap **Cancelar**; verify clipboard is unchanged and highlight disappears.
4. Repeat and tap **Copiar**; verify Toast “Texto copiado”.
5. Paste into another app; verify indentation and line breaks are preserved.
6. Tap a TUI tab afterward; verify mouse clicking still works and Gboard stays closed.

Capture screenshots of the selection with the bar and of the pasted text if possible.

- [ ] **Step 6: Commit the copy bar**

```bash
git add vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java
git commit -m "feat: add terminal selection copy bar"
```

---

### Task 4: Regression and device acceptance

**Files:**
- Modify: `docs/superpowers/plans/2026-10-28-touch-selection.md` (checkboxes only)
- Evidence: `docs/evidence/phone-touch-selection-*.png`

**Interfaces:**
- Consumes all previous tasks.

- [ ] **Step 1: Run the full unit suite**

```bash
export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21
export ANDROID_HOME=/home/HyprKlavier/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:/home/HyprKlavier/.local/opt/gradle/bin:$PATH"
gradle testDebugUnitTest --no-daemon
```

Expected: all tests pass.

- [ ] **Step 2: Run the full build**

```bash
./build.sh
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the device acceptance checklist**

On the S26+:

- [ ] Single tap on a TUI tab switches sessions.
- [ ] Single tap on terminal content sends a click to the TUI.
- [ ] Long-press on code starts selection at the correct cell.
- [ ] Drag down selects multiple lines.
- [ ] Drag up from a lower line selects the reverse interval correctly.
- [ ] Dragging to the top/bottom auto-scrolls and extends selection.
- [ ] Release shows **Copiar/Cancelar**.
- [ ] **Copiar** copies exact indentation and line breaks.
- [ ] **Cancelar** leaves clipboard unchanged.
- [ ] Gboard does not open automatically.
- [ ] The custom keyboard remains visible and usable.
- [ ] A tap after copy/cancel does not send a ghost click.

- [ ] **Step 4: Capture evidence**

```bash
adb exec-out screencap -p > docs/evidence/phone-touch-selection-$(date +%H%M%S).png
```

Capture at least: a multi-line selection with the action bar, the copied text pasted into another app, and the terminal after cancel.

- [ ] **Step 5: Final review and commit**

```bash
git status --short
git diff --check
```

Verify no API key, token, or selected terminal content appears in logs, screenshots, or diffs. Commit only the files changed by this plan after the device checks pass:

```bash
git add vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/SelectionRange.java \
        vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java \
        app/src/test/java/dev/opencode/terminal/SelectionRangeTest.java \
        docs/superpowers/specs/2026-10-28-touch-selection-design.md \
        docs/superpowers/plans/2026-10-28-touch-selection.md
git commit -m "feat: add touch text selection to terminal"
```
