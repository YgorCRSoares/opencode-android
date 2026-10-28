package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Testes do EnvBuilder — mapa de ambiente exato do spawn (spec §5.3). */
public class EnvBuilderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void baseEnvHasExactValues() {
        // ainda não existe em disco: build não pode criá-lo (AppDirs é a Task 10)
        File filesDir = new File(temporaryFolder.getRoot(), "files");
        String base = filesDir.getAbsolutePath();

        Map<String, String> env = EnvBuilder.build(filesDir, "anthropic", null);

        assertEquals(11, env.size());
        assertEquals(base + "/home", env.get("HOME"));
        assertEquals(base + "/prefix", env.get("PREFIX"));
        assertEquals(base + "/tmp", env.get("TMPDIR"));
        assertEquals(base + "/bin:/system/bin", env.get("PATH"));
        assertEquals("/system/bin/sh", env.get("SHELL"));
        assertEquals("xterm-256color", env.get("TERM"));
        assertEquals("C.UTF-8", env.get("LANG"));
        assertEquals(base + "/home/.config", env.get("XDG_CONFIG_HOME"));
        assertEquals(base + "/home/.local/share", env.get("XDG_DATA_HOME"));
        assertEquals(base + "/home/.cache", env.get("XDG_CACHE_HOME"));
        assertEquals("true", env.get("OPENCODE_DISABLE_AUTOUPDATE"));
        assertFalse("build não pode criar diretórios em disco", filesDir.exists());
    }

    @Test
    public void runtimeEnvPointsToNativeLibraries() {
        File filesDir = temporaryFolder.getRoot();
        String nativeLibDir = new File(filesDir, "native-libs").getAbsolutePath();

        Map<String, String> env = EnvBuilder.build(filesDir, nativeLibDir, "anthropic", null);

        assertEquals("base (11) + runtime (3)", 14, env.size());
        assertEquals(nativeLibDir + "/libopentui.so", env.get("OPENTUI_LIB_PATH"));
        assertEquals(nativeLibDir, env.get("LD_LIBRARY_PATH"));
        assertEquals("true", env.get("OPENCODE_EXPERIMENTAL_DISABLE_FILEWATCHER"));
    }

    @Test
    public void runtimeEnvWithKeyAddsOnlyTheKeyVar() {
        File filesDir = temporaryFolder.getRoot();
        String nativeLibDir = new File(filesDir, "native-libs").getAbsolutePath();

        Map<String, String> env = EnvBuilder.build(filesDir, nativeLibDir, "openai", "sk-oai");

        assertEquals(15, env.size());
        assertEquals("sk-oai", env.get("OPENAI_API_KEY"));
    }

    @Test
    public void anthropicKeyExportsAnthropicVar() {
        File filesDir = temporaryFolder.getRoot();

        Map<String, String> env = EnvBuilder.build(filesDir, "anthropic", "sk-ant-teste");

        assertEquals("sk-ant-teste", env.get("ANTHROPIC_API_KEY"));
        assertEquals("base (11) + exatamente uma key", 12, env.size());
    }

    /** Demais provedores conhecidos do mapeamento pinado. */
    @Test
    public void otherKnownProvidersExportExactVars() {
        File filesDir = temporaryFolder.getRoot();

        assertEquals("sk-oai", EnvBuilder.build(filesDir, "openai", "sk-oai").get("OPENAI_API_KEY"));
        assertEquals("sk-gem", EnvBuilder.build(filesDir, "google", "sk-gem").get("GEMINI_API_KEY"));
        assertEquals("sk-or", EnvBuilder.build(filesDir, "openrouter", "sk-or")
                .get("OPENROUTER_API_KEY"));
    }

    @Test
    public void nullKeyExportsNoKeyVars() {
        File filesDir = temporaryFolder.getRoot();

        Map<String, String> nula = EnvBuilder.build(filesDir, "anthropic", null);
        Map<String, String> vazia = EnvBuilder.build(filesDir, "anthropic", "");

        assertEquals("nula: só a base", 11, nula.size());
        assertEquals("vazia: só a base", 11, vazia.size());
        assertNoKeyVars(nula);
        assertNoKeyVars(vazia);
    }

    @Test
    public void unknownProviderExportsNoKeyVars() {
        File filesDir = temporaryFolder.getRoot();

        Map<String, String> env = EnvBuilder.build(filesDir, "deepseek", "sk-desc");

        assertEquals("desconhecido: só a base", 11, env.size());
        assertNoKeyVars(env);
    }

    private static void assertNoKeyVars(Map<String, String> env) {
        for (String chave : env.keySet()) {
            assertFalse("nenhuma var de key esperada, mas encontrou: " + chave,
                    chave.endsWith("_API_KEY"));
        }
    }
}
