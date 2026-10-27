package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import dev.opencode.terminal.RuntimeInstaller.InstallResult;
import dev.opencode.terminal.RuntimeInstaller.ProgressListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Testes do RuntimeInstaller — extração do zip de runtime com marker de versão. */
public class RuntimeInstallerTest {

    private static final String VERSION = "1.0.0";
    private static final String ENTRY_BIN = "bin/opencode";
    private static final String ENTRY_SHARE = "share/banner.txt";
    private static final String CONTENT_BIN = "conteudo-binario-v1\n";
    private static final String CONTENT_SHARE = "banner do runtime\n";
    private static final String ENTRY_PADRAO = "share/padrao.txt";
    /** Altamente compressível: descomprimido ≫ comprimido no zip. */
    private static final String CONTEUDO_GRANDE = "a".repeat(64 * 1024);
    private static final String MARKER = ".extracted_marker";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void freshInstallExtractsAndWritesMarker() throws Exception {
        File baseDir = temporaryFolder.newFolder("files");
        // baseDir contém dados do usuário que não podem ser apagados
        write(new File(baseDir, "home/user.txt"), "dados do usuario\n");
        byte[] zip = zipFixture();
        RecordingListener listener = new RecordingListener();

        InstallResult result = RuntimeInstaller.install(baseDir, new ByteArrayInputStream(zip),
                zip.length, VERSION, listener);

        assertEquals(InstallResult.EXTRACTED, result);
        assertEquals(CONTENT_BIN, read(new File(baseDir, ENTRY_BIN)));
        assertEquals(CONTENT_SHARE, read(new File(baseDir, ENTRY_SHARE)));
        assertEquals(VERSION, read(new File(baseDir, MARKER)));
        assertEquals("dados do usuario\n", read(new File(baseDir, "home/user.txt")));

        assertFalse("listener deve ser chamado durante a extracao", listener.calls.isEmpty());
        for (long[] call : listener.calls) {
            assertEquals("totalBytes repassado ao listener", zip.length, call[1]);
            // bytesExtracted conta os bytes COMPRIMIDOS lidos de `zip` — mesma unidade
            // de totalBytes, para o consumidor dividir sem estourar 100%
            assertTrue("bytesExtracted comprimido: 0 < " + call[0] + " <= " + call[1],
                    call[0] > 0 && call[0] <= call[1]);
        }
    }

    @Test
    public void secondInstallSameVersionIsUpToDate() throws Exception {
        File baseDir = temporaryFolder.newFolder("files");
        byte[] zip = zipFixture();
        assertEquals(InstallResult.EXTRACTED, RuntimeInstaller.install(baseDir,
                new ByteArrayInputStream(zip), zip.length, VERSION, new RecordingListener()));

        // qualquer leitura deste stream indicaria que a extracao foi feita sem necessidade
        RecordingListener listener = new RecordingListener();
        InstallResult result = RuntimeInstaller.install(baseDir, new ExplodingStream(),
                zip.length, VERSION, listener);

        assertEquals(InstallResult.UPTODATE, result);
        assertTrue("listener nao pode ser chamado em UPTODATE", listener.calls.isEmpty());
        assertEquals(CONTENT_BIN, read(new File(baseDir, ENTRY_BIN)));
        assertEquals(VERSION, read(new File(baseDir, MARKER)));
    }

    @Test
    public void interruptedExtractionLeavesNoMarker() {
        File baseDir = new File(temporaryFolder.getRoot(), "files");
        assertTrue(baseDir.mkdirs());
        byte[] zip = zipFixture();

        try {
            RuntimeInstaller.install(baseDir, new FailAfterFourBytes(zip), zip.length, VERSION,
                    new RecordingListener());
            fail("deveria propagar IOException");
        } catch (IOException esperada) {
            // esperado: a falha do stream propaga
        }

        assertFalse("marker nao pode existir apos falha",
                new File(baseDir, MARKER).exists());
    }

    @Test
    public void rerunAfterFailureOverwritesPartialFiles() throws Exception {
        File baseDir = temporaryFolder.newFolder("files");
        byte[] zip = zipFixture();

        try {
            RuntimeInstaller.install(baseDir, new FailAfterFourBytes(zip), zip.length, VERSION,
                    new RecordingListener());
            fail("deveria propagar IOException");
        } catch (IOException esperada) {
            // primeira tentativa falhou no meio
        }

        // A falha aos 4 bytes aborta antes de qualquer entry; semeia os debris de uma
        // extracao interrompida para provar que a re-execucao sobrescreve.
        write(new File(baseDir, ENTRY_BIN), "PARCIAL-QUEBRADO");

        InstallResult result = RuntimeInstaller.install(baseDir, new ByteArrayInputStream(zip),
                zip.length, VERSION, new RecordingListener());

        assertEquals(InstallResult.EXTRACTED, result);
        assertEquals(CONTENT_BIN, read(new File(baseDir, ENTRY_BIN)));
        assertEquals(CONTENT_SHARE, read(new File(baseDir, ENTRY_SHARE)));
        assertEquals(VERSION, read(new File(baseDir, MARKER)));
    }

    /**
     * Os dois argumentos do listener precisam ser conmensuráveis: o consumidor (Task 10)
     * passa o tamanho COMPRIMIDO do asset como totalBytes e divide os dois. Contando bytes
     * descomprimidos, o progresso estouraria 100% antes do fim do zip.
     */
    @Test
    public void listenerProgressUsesCompressedBytes() throws Exception {
        File baseDir = temporaryFolder.newFolder("files");
        byte[] zip = zipCompressivel();
        // Garante que o teste discrimina: descomprimido ≫ comprimido neste fixture
        assertTrue("fixture: descomprimido deve exceder o tamanho comprimido do zip",
                CONTEUDO_GRANDE.length() > zip.length);

        RecordingListener listener = new RecordingListener();
        RuntimeInstaller.install(baseDir, new ByteArrayInputStream(zip), zip.length, VERSION,
                listener);

        assertFalse(listener.calls.isEmpty());
        long anterior = 0;
        for (long[] call : listener.calls) {
            assertEquals("totalBytes e o tamanho comprimido do zip", zip.length, call[1]);
            assertTrue("bytesExtracted deve ser comprimido: 0 < " + call[0] + " <= " + call[1],
                    call[0] > 0 && call[0] <= call[1]);
            assertTrue("progresso monotono", call[0] >= anterior);
            anterior = call[0];
        }
    }

    // -- fixtures --

    /** Zip pequeno com 2 entries em diretorios ainda inexistentes. */
    private static byte[] zipFixture() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                writeEntry(zip, ENTRY_BIN, CONTENT_BIN);
                writeEntry(zip, ENTRY_SHARE, CONTENT_SHARE);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError("falha ao montar fixture de zip", e);
        }
    }

    /** Zip com conteúdo altamente compressível — descomprimido bem maior que o arquivo. */
    private static byte[] zipCompressivel() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                writeEntry(zip, ENTRY_PADRAO, CONTEUDO_GRANDE);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError("falha ao montar fixture de zip", e);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class RecordingListener implements ProgressListener {
        private final List<long[]> calls = new ArrayList<>();

        @Override
        public void onProgress(long bytesExtracted, long totalBytes) {
            calls.add(new long[] {bytesExtracted, totalBytes});
        }
    }

    /** Deixa passar exatamente 4 bytes e entao falha — simulando extracao interrompida. */
    private static final class FailAfterFourBytes extends InputStream {
        private final InputStream delegate;
        private int entregues;

        private FailAfterFourBytes(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() throws IOException {
            falhaSeCompleto();
            int b = delegate.read();
            if (b != -1) {
                entregues++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            falhaSeCompleto();
            int n = delegate.read(b, off, Math.min(len, 4 - entregues));
            if (n > 0) {
                entregues += n;
            }
            return n;
        }

        private void falhaSeCompleto() throws IOException {
            if (entregues >= 4) {
                throw new IOException("stream de zip interrompido apos 4 bytes");
            }
        }
    }

    /** Qualquer leitura aqui provaria que o zip foi aberto sem necessidade. */
    private static final class ExplodingStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("zip nao deveria ser lido em UPTODATE");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException("zip nao deveria ser lido em UPTODATE");
        }
    }
}
