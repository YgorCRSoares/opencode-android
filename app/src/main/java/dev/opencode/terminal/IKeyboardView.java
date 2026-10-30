package dev.opencode.terminal;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Teclado embutido.
 */
public class IKeyboardView extends FrameLayout implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "IKeyboardView";

    private AccentKeyboardView mKeyboardView;
    private Keyboard mQwertyKeyboard;
    private Keyboard mSymbolKeyboard;
    private Keyboard mExtraKeyboard;
    private Keyboard mNumpadKeyboard;

    private enum Layer { QWERTY, SYMBOL, SYMBOLEXTRA, NUMPAD }
    private Layer mCurrentLayer = Layer.QWERTY;

    private boolean mIsShift = false;
    private boolean mIsCtrl = false;

    private TermController mController;

    private Drawable mShiftIcon;
    private CharSequence mCtrlLabel;

    public IKeyboardView(Context context, TermController controller) {
        super(context);
        mController = controller;

        View keyboardView = LayoutInflater.from(context).inflate(R.layout.keyboard, this);
        mKeyboardView = keyboardView.findViewById(R.id.keyboard);
        mKeyboardView.setAccentSink(new AccentKeyboardView.AccentSink() {
            @Override
            public void onAccentSelected(String ch) {
                writeBytes(ch.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                disarmModifiers();
            }
        });
        mKeyboardView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    mKeyboardView.dismissAccentPopup();
                }
                return false;
            }
        });

        mQwertyKeyboard = new Keyboard(context, R.xml.qwerty);
        mSymbolKeyboard = new Keyboard(context, R.xml.symbol);
        mExtraKeyboard = new Keyboard(context, R.xml.symbolextra);
        mNumpadKeyboard = new Keyboard(context, R.xml.numpad);

        List<Keyboard.Key> keys = mQwertyKeyboard.getKeys();
        for (Keyboard.Key key : keys) {
            if (key.codes != null && key.codes.length > 0) {
                if (key.codes[0] == -1) mShiftIcon = key.icon;
                if (key.codes[0] == -10) mCtrlLabel = key.label;
            }
        }

        mKeyboardView.setKeyboard(mQwertyKeyboard);
        mKeyboardView.setOnKeyboardActionListener(this);
    }

    @Override
    public void onPress(int keyCode) {}

    @Override
    public void onRelease(int keyCode) {}

    @Override
    public void onKey(int keyCode, int[] keyCodes) {
        if (mController == null) return;

        // 1. Shift
        if (keyCode == -1) {
            mIsShift = !mIsShift;
            mKeyboardView.setShifted(mIsShift);
            setShiftVisual(mIsShift);
            mKeyboardView.invalidateAllKeys();
            return;
        }

        // 2. Ctrl
        if (keyCode == -10) {
            mIsCtrl = !mIsCtrl;
            setCtrlVisual(mIsCtrl);
            mKeyboardView.invalidateAllKeys();
            // In termux-app, Ctrl is handled by the View's key handler or manually.
            // We'll keep our own mIsCtrl state and use it in onKey.
            return;
        }

        // 3. KB
        if (keyCode == -9) {
            disarmModifiers();
            mController.showSoftKeyboard();
            return;
        }

        // 4. Layers
        if (keyCode == -2) {
            switchLayer(mCurrentLayer == Layer.QWERTY ? Layer.SYMBOL : Layer.QWERTY);
            return;
        }
        if (keyCode == -7) {
            switchLayer(mCurrentLayer == Layer.SYMBOL ? Layer.SYMBOLEXTRA : Layer.SYMBOL);
            return;
        }
        if (keyCode == -8) {
            switchLayer(Layer.NUMPAD);
            return;
        }

        // 5. Bytes
        byte[] bytes = null;
        if (mIsCtrl) bytes = KeyEncoding.ctrlByte(keyCode);
        if (bytes == null) bytes = KeyEncoding.actionBytes(keyCode);
        if (bytes == null) bytes = KeyEncoding.arrowBytes(keyCode);
        if (bytes == null) {
            if (mIsShift && keyCode >= 97 && keyCode <= 122) {
                bytes = KeyEncoding.charBytes(keyCode - 32);
            } else {
                bytes = KeyEncoding.charBytes(keyCode);
            }
        }

        if (bytes != null) {
            writeBytes(bytes);
            if (keyCode != -5) disarmModifiers();
        }
    }

    private void writeBytes(byte[] bytes) {
        try {
            if (mController != null) {
                // Use the new write method from TermController
                if (bytes != null && bytes.length > 0) {
                    mController.write(bytes, 0, bytes.length);
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Exception", e);
        }
    }

    private void setShiftVisual(boolean shifted) {
        for (Keyboard.Key key : mQwertyKeyboard.getKeys()) {
            if (key.codes != null && key.codes.length > 0 && key.codes[0] == -1) {
                if (shifted) {
                    key.icon = null;
                    key.label = "⇧";
                } else {
                    key.icon = mShiftIcon;
                    key.label = null;
                }
            }
        }
    }

    private void setCtrlVisual(boolean ctrl) {
        for (Keyboard.Key key : mQwertyKeyboard.getKeys()) {
            if (key.codes != null && key.codes.length > 0 && key.codes[0] == -10) {
                key.label = ctrl ? "⌃CTRL" : mCtrlLabel;
            }
        }
    }

    private void disarmModifiers() {
        mIsShift = false;
        mIsCtrl = false;
        mKeyboardView.setShifted(false);
        setShiftVisual(false);
        setCtrlVisual(false);
        mKeyboardView.invalidateAllKeys();
    }

    private void switchLayer(Layer layer) {
        mCurrentLayer = layer;
        mKeyboardView.dismissAccentPopup();
        switch (layer) {
            case SYMBOL: mKeyboardView.setKeyboard(mSymbolKeyboard); break;
            case SYMBOLEXTRA: mKeyboardView.setKeyboard(mExtraKeyboard); break;
            case NUMPAD: mKeyboardView.setKeyboard(mNumpadKeyboard); break;
            case QWERTY: default: mKeyboardView.setKeyboard(mQwertyKeyboard); break;
        }
        disarmModifiers();
        mKeyboardView.invalidateAllKeys();
    }

    @Override
    public void onText(CharSequence text) {
        if (text == null) return;
        writeBytes(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void swipeLeft() {}

    @Override
    public void swipeRight() {}

    @Override
    public void swipeUp() {}

    @Override
    public void swipeDown() {}
}
