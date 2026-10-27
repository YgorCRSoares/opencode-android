package dev.opencode.terminal;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Fixed row of extra keys (Esc, Ctrl, Tab, arrows, /) rendered above the
 * soft keyboard. Writes the mapped raw bytes to the PTY master obtained from
 * {@link TermController#outputStream()}.
 *
 * <p>Ctrl is one-shot: pressing it arms the modifier; the next dispatched key
 * consumes it and disarms.</p>
 */
public class ExtraKeysView extends LinearLayout {

    private final TermController mController;

    private boolean mCtrlArmed;
    private Button mCtrlButton;

    private static final String[] KEY_LABELS = new String[] {
            "ESC", "Ctrl", "Tab", "\u2190", "\u2191", "\u2193", "\u2192", "/"
    };
    private static final String[] KEY_CODES = new String[] {
            "ESC", "CTRL", "TAB", "LEFT", "UP", "DOWN", "RIGHT", "/"
    };

    public ExtraKeysView(Context context, TermController controller) {
        super(context);
        mController = controller;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(0xFF222222);

        for (int i = 0; i < KEY_LABELS.length; i++) {
            final String code = KEY_CODES[i];
            Button btn = new Button(context);
            btn.setText(KEY_LABELS[i]);
            btn.setFocusable(false);
            btn.setMinWidth(0);
            btn.setMinHeight(0);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleKey(code);
                }
            });
            if ("CTRL".equals(code)) {
                mCtrlButton = btn;
            }
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            addView(btn, lp);
        }
    }

    private void handleKey(String code) {
        if ("CTRL".equals(code)) {
            mCtrlArmed = true;
            updateCtrlColor();
            if (mController != null) {
                mController.sendCtrl();
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCtrlArmed = false;
                    updateCtrlColor();
                }
            }, 3000L);
            return;
        }

        boolean ctrlWasArmed = mCtrlArmed;
        String mapperKey = code;
        if ("CTRL".equals(code)) {
            // never here
        }

        byte[] data = KeyMapper.bytesFor(mapperKey, ctrlWasArmed);
        OutputStream out = (mController == null) ? null : mController.outputStream();
        if (out != null && data.length > 0) {
            try {
                out.write(data);
                out.flush();
            } catch (IOException ignored) {
            }
        }

        // One-shot: disarm after dispatching any non-Ctrl key.
        if (ctrlWasArmed) {
            mCtrlArmed = false;
            updateCtrlColor();
        }
    }

    private void updateCtrlColor() {
        if (mCtrlButton == null) return;
        mCtrlButton.setBackgroundColor(mCtrlArmed ? 0xFF4CAF50 : 0xFF555555);
    }
}
