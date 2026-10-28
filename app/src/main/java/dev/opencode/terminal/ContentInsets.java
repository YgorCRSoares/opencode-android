package dev.opencode.terminal;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;

/**
 * Corrige o layout edge-to-edge do Android 15+/16 (targetSdk 35+): o conteúdo
 * nasce em [0,0] atrás da status bar e da ActionBar, escondendo o formulário
 * de setup e as primeiras linhas do terminal. Aplica padding equivalente à
 * status bar + ActionBar no topo e à barra de navegação no rodapé.
 *
 * <p>Funciona tanto quando o framework já posiciona o conteúdo abaixo da
 * ActionBar (missingTop=0) quanto no modo overlay (missingTop=altura da barra).
 */
final class ContentInsets {
    private ContentInsets() {}

    static void apply(Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        final int actionBarHeight = actionBarHeight(activity);
        final View actionBar = findActionBar(activity);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            final int topInset;
            final int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.post(() -> {
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                int desiredTop;
                if (actionBar != null && actionBar.getHeight() > 0) {
                    int[] actionBarLocation = new int[2];
                    actionBar.getLocationOnScreen(actionBarLocation);
                    desiredTop = actionBarLocation[1] + actionBar.getHeight();
                } else {
                    // Fallback: em Android 16 o inset de systemBars já inclui a
                    // ActionBar (status 136 + barra 147 = 283); em versões sem
                    // edge-to-edge o inset traz só a status bar e a ActionBar
                    // ainda precisa ser somada. Se o ID da ActionBar não existir
                    // (ex.: alguma ROM), o inset maior que a barra indica que ela
                    // já está contida; somar de novo abriria um vão de 147 px.
                    desiredTop = topInset >= actionBarHeight
                            ? topInset
                            : topInset + actionBarHeight;
                }
                int missingTop = Math.max(0, desiredTop - location[1]);
                v.setPadding(0, missingTop, 0, bottomInset);
            });
            return insets;
        });
        content.requestApplyInsets();
    }

    private static View findActionBar(Activity activity) {
        int id = activity.getResources().getIdentifier(
                "action_bar_container", "id", "android");
        if (id == 0) return null;
        return activity.getWindow().getDecorView().findViewById(id);
    }

    private static int actionBarHeight(Activity activity) {
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.actionBarSize, value, true)) {
            return TypedValue.complexToDimensionPixelSize(
                    value.data, activity.getResources().getDisplayMetrics());
        }
        return 0;
    }
}
