package dev.opencode.terminal;

import android.content.Context;

/**
 * Top-level alias para {@link SettingsStore.PrefsSettingsStore}, para conveniência de
 * instanciação pelos consumidores (Task 10, MainActivity, etc.).
 */
public class PrefsSettingsStore extends SettingsStore.PrefsSettingsStore {

    public PrefsSettingsStore(Context context) {
        super(context);
    }
}
