package dev.opencode.terminal;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

/**
 * Carrega a fonte monoespaçada embarcada usada pelo renderer do terminal.
 *
 * <p>A fonte precisa cobrir os glifos Unicode do logo/spinner da TUI (blocos,
 * braille, formas geométricas). Se o asset estiver ausente ou corrompido,
 * voltamos para {@link Typeface#MONOSPACE} em vez de derrubar a sessão.</p>
 */
final class TerminalTypeface {

    private static final String TAG = "TerminalTypeface";
    private static final String ASSET = "fonts/AdwaitaMono-Regular.ttf";

    private TerminalTypeface() {
    }

    static Typeface load(Context context) {
        try {
            Typeface typeface = Typeface.createFromAsset(context.getAssets(), ASSET);
            Log.i(TAG, "fonte carregada: " + typeface);
            return typeface;
        } catch (RuntimeException e) {
            Log.w(TAG, "fonte embarcada indisponível; usando monospace", e);
            return Typeface.MONOSPACE;
        }
    }
}
