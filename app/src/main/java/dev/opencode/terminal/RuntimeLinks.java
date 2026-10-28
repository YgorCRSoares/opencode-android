package dev.opencode.terminal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Liga os nomes que o OpenCode procura no {@code PATH} aos executáveis que o
 * Android mantém no diretório nativo do APK.
 *
 * <p>O {@code rg} é procurado pelo nome. O arquivo em {@code files/bin/rg} é um
 * symlink para {@code nativeLibraryDir/librg.so}; o kernel resolve o link e
 * aplica a permissão/contexto do arquivo nativo, contornando a proibição de
 * executar arquivos graváveis do app (Android 10+, targetSdk >= 29).</p>
 */
final class RuntimeLinks {

    private RuntimeLinks() {
    }

    static void ensureRipgrepLink(File filesDir, File nativeLibDir) throws IOException {
        Path binDir = new File(filesDir, "bin").toPath();
        Files.createDirectories(binDir);

        Path target = new File(nativeLibDir, "librg.so").toPath();
        if (!Files.isRegularFile(target)) {
            throw new IOException("ripgrep nativo ausente: " + target);
        }

        Path link = binDir.resolve("rg");
        if (Files.isSymbolicLink(link)) {
            try {
                if (Files.readSymbolicLink(link).equals(target)) {
                    return;
                }
            } catch (IOException ignored) {
                // Link quebrado/ilegível: recria abaixo.
            }
        }
        Files.deleteIfExists(link);
        Files.createSymbolicLink(link, target);
    }
}
