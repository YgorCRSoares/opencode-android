package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsStoreTest {

    @Test
    public void saveRoundTrip() {
        SettingsStore store = new SettingsStore.InMemorySettingsStore();
        store.save("anthropic", "sk-ant-teste");
        assertTrue(store.setupDone());
        assertEquals("anthropic", store.provider());
        assertEquals("sk-ant-teste", store.apiKey());
    }

    @Test
    public void markSkippedFlipsSetupDoneAndClearsKey() {
        SettingsStore store = new SettingsStore.InMemorySettingsStore();
        store.markSkipped();
        assertTrue(store.setupDone());
        assertNull(store.apiKey());
    }
}
