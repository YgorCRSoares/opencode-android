package dev.opencode.terminal;

import java.io.File;

/**
 * Garante que a árvore de diretórios usada por {@link EnvBuilder} exista antes
 * do spawn. Criação idempotente via {@code mkdirs()}.
 */
public final class AppDirs {

    private AppDirs() {
    }

    /**
     * Cria {@code home/.config}, {@code home/.local/share}, {@code home/.cache},
     * {@code prefix}, {@code tmp} e {@code bin} sob {@code filesDir}.
     */
    public static void ensure(File filesDir) {
        if (filesDir == null) return;
        mkdirp(new File(filesDir, "home"));
        mkdirp(new File(filesDir, "home/.config"));
        mkdirp(new File(filesDir, "home/.local"));
        mkdirp(new File(filesDir, "home/.local/share"));
        mkdirp(new File(filesDir, "home/.cache"));
        mkdirp(new File(filesDir, "prefix"));
        mkdirp(new File(filesDir, "tmp"));
        mkdirp(new File(filesDir, "bin"));
    }

    private static void mkdirp(File dir) {
        if (dir.isDirectory()) return;
        dir.mkdirs();
    }
}
