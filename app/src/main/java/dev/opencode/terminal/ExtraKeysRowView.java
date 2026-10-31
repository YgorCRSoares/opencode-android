package dev.opencode.terminal;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import java.nio.charset.StandardCharsets;

/**
 * Fila de teclas extras estilo Termux (Esc, Ctrl, Alt, Shift, Tab, ←, ↑, ↓, →).
 */
public class ExtraKeysRowView extends LinearLayout {

    private final TermController mController;
    private Button mBtnCtrl;
    private Button mBtnAlt;

    public ExtraKeysRowView(Context context, TermController controller) {
        super(context);
        mController = controller;

        setOrientation(LinearLayout.HORIZONTAL);
        setBackgroundColor(0xFF1E1E1E);
        setGravity(Gravity.CENTER_VERTICAL);

        // Altura fixa ~40-48dp (usamos 44dp convertidos para pixels)
        float density = context.getResources().getDisplayMetrics().density;
        int heightPx = (int) (44 * density);
        setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

        // Não rouba foco de forma permanente
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        String[] keys = {"Esc", "Ctrl", "Alt", "Modo", "←", "↑", "↓", "→"};

        for (String keyText : keys) {
            Button btn = new Button(context);
            btn.setText(keyText);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(13f);
            btn.setPadding(0, 0, 0, 0);
            btn.setAllCaps(false);
            btn.setBackgroundColor(Color.TRANSPARENT);

            // Cada botão ganha peso igual
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            // Pequena margem horizontal entre botões para distinção visual limpa
            int margin = (int) (1 * density);
            lp.setMargins(margin, margin, margin, margin);
            btn.setLayoutParams(lp);

            btn.setFocusable(false);
            btn.setClickable(true);

            if (keyText.equals("Ctrl")) mBtnCtrl = btn;
            else if (keyText.equals("Alt")) mBtnAlt = btn;

            btn.setOnClickListener(v -> handleKeyClick(keyText));

            addView(btn);
        }
    }

    private void handleKeyClick(String keyText) {
        if (mController == null) return;

        switch (keyText) {
            case "Esc":
                mController.write(new byte[]{0x1B}, 0, 1);
                break;
            case "Ctrl":
                boolean newCtrl = !mController.isCtrlLatched();
                mController.setCtrlLatched(newCtrl);
                updateLatchVisuals();
                break;
            case "Alt":
                boolean newAlt = !mController.isAltLatched();
                mController.setAltLatched(newAlt);
                updateLatchVisuals();
                break;
            case "Modo":
                // Envia Shift+Tab como CSI Z (ESC [ Z)
                mController.write(new byte[]{0x1B, 0x5B, 0x5A}, 0, 3);
                break;
            case "←":
                mController.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT);
                updateLatchVisuals();
                break;
            case "↑":
                mController.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP);
                updateLatchVisuals();
                break;
            case "↓":
                mController.sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN);
                updateLatchVisuals();
                break;
            case "→":
                mController.sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT);
                updateLatchVisuals();
                break;
        }

        // Refocaliza o terminal e reabre o teclado do sistema para o IME não sumir
        mController.showSoftKeyboard();
    }

    public void updateLatchVisuals() {
        if (mBtnCtrl != null) {
            mBtnCtrl.setBackgroundColor(mController.isCtrlLatched() ? 0xFF007ACC : Color.TRANSPARENT);
        }
        if (mBtnAlt != null) {
            mBtnAlt.setBackgroundColor(mController.isAltLatched() ? 0xFF007ACC : Color.TRANSPARENT);
        }
    }
}
