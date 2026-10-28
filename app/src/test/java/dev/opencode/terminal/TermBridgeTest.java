package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TermBridgeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void arm64WithWrapperIsRuntime() {
        TermBridge.Plan plan = TermBridge.planFor(new String[] {"arm64-v8a"}, true);
        assertEquals(TermBridge.Mode.RUNTIME, plan.mode);
        assertEquals("ok", plan.reason);
    }

    @Test
    public void x86WithArm64SecondaryIsFallback() {
        // arm64 via libndk_translation não é ABI primária: o exec do app
        // retorna EACCES no AVD, então o shell de diagnóstico é o caminho seguro.
        TermBridge.Plan plan = TermBridge.planFor(new String[] {"x86_64", "arm64-v8a"}, true);
        assertEquals(TermBridge.Mode.FALLBACK, plan.mode);
        assertEquals("arm64_only", plan.reason);
    }

    @Test
    public void x86OnlyIsFallbackArm64Only() {
        TermBridge.Plan plan = TermBridge.planFor(new String[] {"x86_64"}, false);
        assertEquals(TermBridge.Mode.FALLBACK, plan.mode);
        assertEquals("arm64_only", plan.reason);
    }

    @Test
    public void arm64MissingWrapperIsFallbackMissingRuntime() {
        TermBridge.Plan plan = TermBridge.planFor(new String[] {"arm64-v8a"}, false);
        assertEquals(TermBridge.Mode.FALLBACK, plan.mode);
        assertEquals("missing_runtime", plan.reason);
    }

    @Test
    public void appDirsEnsuresAllDirs() {
        File filesDir = new File(temporaryFolder.getRoot(), "files");
        AppDirs.ensure(filesDir);
        assertTrue(new File(filesDir, "home/.config").isDirectory());
        assertTrue(new File(filesDir, "home/.local/share").isDirectory());
        assertTrue(new File(filesDir, "home/.cache").isDirectory());
        assertTrue(new File(filesDir, "prefix").isDirectory());
        assertTrue(new File(filesDir, "tmp").isDirectory());
        assertTrue(new File(filesDir, "bin").isDirectory());
    }
}
