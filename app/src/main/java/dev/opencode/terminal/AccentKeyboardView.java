package dev.opencode.terminal;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public class AccentKeyboardView extends KeyboardView {
    public interface AccentSink {
        void onAccentSelected(String ch);
    }

    private AccentSink sink;
    private PopupWindow popupWindow;
    private boolean mShifted;

    public AccentKeyboardView(Context context) {
        this(context, null);
    }

    public AccentKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccentKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAccentSink(AccentSink sink) {
        this.sink = sink;
    }

    public void dismissAccentPopup() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    @Override
    public boolean setShifted(boolean shifted) {
        mShifted = shifted;
        return super.setShifted(shifted);
    }

    @Override
    protected boolean onLongPress(Keyboard.Key popupKey) {
        CharSequence label = popupKey.label;
        if (label == null || label.length() != 1) {
            return super.onLongPress(popupKey);
        }

        String variants = AccentMap.variantsFor(label.charAt(0), mShifted);
        if (variants.isEmpty()) {
            return super.onLongPress(popupKey);
        }

        // Create popup container
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setBackground(new ColorDrawable(0xF0222222));
        container.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        container.setElevation(dpToPx(8));

        // Add variant buttons
        for (char ch : variants.toCharArray()) {
            TextView textView = new TextView(getContext());
            textView.setTextSize(24);
            textView.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            textView.setGravity(Gravity.CENTER);
            textView.setBackground(new ColorDrawable(0xFF3D3D3D));
            textView.setTextColor(0xFFFFFFFF);
            textView.setText(String.valueOf(ch));
            textView.setOnClickListener(v -> {
                if (sink != null) {
                    sink.onAccentSelected(String.valueOf(ch));
                }
                dismissAccentPopup();
            });
            container.addView(textView);
        }

        // Measure container
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = container.getMeasuredWidth();
        int popupHeight = container.getMeasuredHeight();

        // Create and show popup
        popupWindow = new PopupWindow(container, popupWidth, popupHeight);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0xF0222222));
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(true);
        popupWindow.setOutsideTouchable(false); // com outsideTouchable o MOVE do dedo sobre o teclado dispara ACTION_OUTSIDE e fecha o pop-up imediatamente; o fechamento é feito por dismissAccentPopup() (touch no teclado, troca de camada ou seleção)
        popupWindow.setElevation(dpToPx(8));

        // Position popup
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0] + popupKey.x + popupKey.width / 2 - popupWidth / 2;
        int y = loc[1] + popupKey.y - popupHeight - dpToPx(8);
        if (y < loc[1]) {
            y = loc[1] + popupKey.height + dpToPx(8);
        }

        popupWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        final PopupWindow pw = popupWindow;
        postDelayed(() -> { if (popupWindow == pw) dismissAccentPopup(); }, 6000); // evita pop-up órfão se o usuário não tocar em nada
        popupWindow.setOnDismissListener(() -> popupWindow = null);

        // AOSP KeyboardView only sets mAbortKey when onLongPress returns true
        return true;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}