package dev.opencode.terminal;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extrai o zip de runtime para o diretório de arquivos do app, gravando um marker
 * de versão para pular a extração em execuções seguintes.
 *
 * <p>Sem wiping de {@code baseDir} (contém dados do usuário): só sobrescreve os
 * entries do zip. Consumido pela Task 10.
 */
public final class RuntimeInstaller {

    /** Arquivo em {@code baseDir} que registra a versão já extraída. */
    private static final String MARKER_NAME = ".extracted_marker";

    /**
     * Progresso da extração.
     *
     * <p>Ambos os argumentos são <strong>bytes comprimidos</strong>: {@code bytesExtracted}
     * conta o que já foi lido de {@code zip} e {@code totalBytes} é o tamanho comprimido
     * total (na Task 10, o tamanho do asset). Assim o consumidor pode dividir os dois
     * sem estourar 100%.
     */
    public interface ProgressListener {
        void onProgress(long bytesExtracted, long totalBytes);
    }

    /** Resultado de {@link #install}. */
    public enum InstallResult {
        EXTRACTED,
        UPTODATE
    }

    private RuntimeInstaller() {
    }

    /**
     * Extrai {@code zip} em {@code baseDir} quando o marker não corresponde a
     * {@code version}.
     *
     * @return {@link InstallResult#UPTODATE} se o marker já registra a versão
     *         (nada é extraído e {@code listener} não é chamado); caso contrário
     *         extrai todos os entries e grava o marker só no fim, retornando
     *         {@link InstallResult#EXTRACTED}
     * @throws IOException se a extração falhar — o marker não é gravado
     */
    public static InstallResult install(File baseDir, InputStream zip, long totalBytes,
            String version, ProgressListener listener) throws IOException {
        File marker = new File(baseDir, MARKER_NAME);
        if (marker.isFile() && version.equals(readMarker(marker))) {
            return InstallResult.UPTODATE;
        }

        extractAll(baseDir, zip, totalBytes, listener);
        writeMarker(marker, version);
        return InstallResult.EXTRACTED;
    }

    private static void extractAll(File baseDir, InputStream zip, long totalBytes,
            ProgressListener listener) throws IOException {
        if (!baseDir.isDirectory() && !baseDir.mkdirs()) {
            throw new IOException("não foi possível criar " + baseDir);
        }
        String raiz = baseDir.getCanonicalPath();
        CountingInputStream contador = new CountingInputStream(zip);
        try (ZipInputStream entries = new ZipInputStream(contador)) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = entries.getNextEntry()) != null) {
                File alvo = new File(baseDir, entry.getName());
                if (!alvo.getCanonicalPath().startsWith(raiz + File.separator)) {
                    throw new IOException("entry fora de baseDir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!alvo.isDirectory() && !alvo.mkdirs()) {
                        throw new IOException("não foi possível criar " + alvo);
                    }
                    continue;
                }
                File pai = alvo.getParentFile();
                if (!pai.isDirectory() && !pai.mkdirs()) {
                    throw new IOException("não foi possível criar " + pai);
                }
                try (BufferedOutputStream out = new BufferedOutputStream(
                        new FileOutputStream(alvo))) {
                    int lido;
                    while ((lido = entries.read(buffer)) != -1) {
                        out.write(buffer, 0, lido);
                        listener.onProgress(contador.getCount(), totalBytes);
                    }
                }
            }
        }
    }

    /**
     * Conta os bytes comprimidos lidos do stream do chamador, para que
     * {@code bytesExtracted} e {@code totalBytes} fiquem na mesma unidade.
     */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }

        private long getCount() {
            return count;
        }
    }

    /** Grava o marker via arquivo temporário + rename, para nunca deixar marker parcial. */
    private static void writeMarker(File marker, String version) throws IOException {
        File temp = new File(marker.getParentFile(), MARKER_NAME + ".tmp");
        Files.write(temp.toPath(), version.getBytes(StandardCharsets.UTF_8));
        // rename(2) substitui o destino atomicamente em Linux/Android
        if (!temp.renameTo(marker)) {
            temp.delete();
            throw new IOException("não foi possível gravar " + marker);
        }
    }

    private static String readMarker(File marker) throws IOException {
        return new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8);
    }
}
