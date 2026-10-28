package dev.opencode.terminal;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Monta o mapa de ambiente do processo spawnado (shell simples da Task 6 e
 * runtime OpenCode da Task 10).
 *
 * <p>Puro: só monta o {@link HashMap} — não cria diretórios (isso é
 * {@code AppDirs} na Task 10) nem lê o ambiente do sistema. Os valores são
 * fixados pelo spec §5.3 e garantidos pelos testes.
 */
public final class EnvBuilder {

    /** Mapeamento provider → variável que recebe a apiKey. */
    private static final Map<String, String> KEY_VARS;

    static {
        Map<String, String> vars = new HashMap<>();
        vars.put("anthropic", "ANTHROPIC_API_KEY");
        vars.put("openai", "OPENAI_API_KEY");
        vars.put("google", "GEMINI_API_KEY");
        vars.put("openrouter", "OPENROUTER_API_KEY");
        KEY_VARS = Collections.unmodifiableMap(vars);
    }

    private EnvBuilder() {
    }

    /**
     * Mapa de ambiente do spawn, ancorado em {@code filesDir}.
     *
     * <p>Sempre contém as entradas base (HOME, PREFIX, TMPDIR, PATH, SHELL,
     * TERM, LANG, XDG_* e OPENCODE_DISABLE_AUTOUPDATE). Exporta uma variável
     * de key somente quando {@code apiKey} não é nula/vazia E {@code provider}
     * é conhecido — provider desconhecido não exporta nada.
     */
    public static Map<String, String> build(File filesDir, String provider, String apiKey) {
        return build(filesDir, null, provider, apiKey);
    }

    /**
     * Variante do runtime: além do ambiente base, aponta o dlopen do
     * {@code libopentui.so} e o carregamento de bibliotecas para o diretório
     * nativo extraído do APK.
     */
    public static Map<String, String> build(File filesDir, String nativeLibDir,
            String provider, String apiKey) {
        String home = new File(filesDir, "home").getAbsolutePath();
        Map<String, String> env = new HashMap<>();
        env.put("HOME", home);
        env.put("PREFIX", new File(filesDir, "prefix").getAbsolutePath());
        env.put("TMPDIR", new File(filesDir, "tmp").getAbsolutePath());
        env.put("PATH", new File(filesDir, "bin").getAbsolutePath() + ":/system/bin");
        env.put("SHELL", "/system/bin/sh");
        env.put("TERM", "xterm-256color");
        env.put("LANG", "C.UTF-8");
        env.put("XDG_CONFIG_HOME", home + "/.config");
        env.put("XDG_DATA_HOME", home + "/.local/share");
        env.put("XDG_CACHE_HOME", home + "/.cache");
        env.put("OPENCODE_DISABLE_AUTOUPDATE", "true");

        if (nativeLibDir != null && !nativeLibDir.isEmpty()) {
            env.put("OPENTUI_LIB_PATH", new File(nativeLibDir, "libopentui.so").getAbsolutePath());
            env.put("LD_LIBRARY_PATH", nativeLibDir);
            env.put("OPENCODE_EXPERIMENTAL_DISABLE_FILEWATCHER", "true");
        }

        if (apiKey != null && !apiKey.isEmpty()) {
            String keyVar = KEY_VARS.get(provider);
            if (keyVar != null) {
                env.put(keyVar, apiKey);
            }
        }
        return env;
    }
}
