package dev.opencode.terminal;

import android.content.Context;
import android.content.SharedPreferences;

public interface SettingsStore {
    String provider();
    String apiKey();
    boolean setupDone();
    void save(String provider, String apiKey);
    void markSkipped();

    class PrefsSettingsStore implements SettingsStore {
        private static final String PREFS = "oc_settings";
        private final SharedPreferences prefs;

        public PrefsSettingsStore(Context context) {
            this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @Override
        public String provider() { return prefs.getString("provider", null); }

        @Override
        public String apiKey() {
            if (prefs.getBoolean("skipped", false)) return null;
            return prefs.getString("key", null);
        }

        @Override
        public boolean setupDone() {
            return prefs.getBoolean("skipped", false)
                    || prefs.getString("provider", null) != null;
        }

        @Override
        public void save(String provider, String apiKey) {
            prefs.edit()
                    .putString("provider", provider)
                    .putString("key", apiKey)
                    .putBoolean("skipped", false)
                    .apply();
        }

        @Override
        public void markSkipped() {
            prefs.edit()
                    .putBoolean("skipped", true)
                    .remove("key")
                    .apply();
        }
    }

    class InMemorySettingsStore implements SettingsStore {
        private String provider;
        private String key;
        private boolean skipped;

        @Override
        public String provider() { return provider; }

        @Override
        public String apiKey() { return skipped ? null : key; }

        @Override
        public boolean setupDone() { return skipped || (provider != null); }

        @Override
        public void save(String provider, String apiKey) {
            this.provider = provider;
            this.key = apiKey;
            this.skipped = false;
        }

        @Override
        public void markSkipped() {
            this.skipped = true;
            this.key = null;
        }
    }
}
