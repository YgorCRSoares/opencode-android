package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RuntimeInstallerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractedRuntimeBinariesAreExecutable() throws Exception {
        File base = temporaryFolder.newFolder("files");
        File zip = runtimeZip();

        RuntimeInstaller.install(base, new FileInputStream(zip), zip.length(), "test", (a, b) -> {
        });

        assertExecutable(base, "opencode/opencode2");
        assertExecutable(base, "opencode/opencode2.bin");
        assertExecutable(base, "bin/rg");
    }

    @Test
    public void uptodateInstallRepairsMissingExecutableBits() throws Exception {
        File base = temporaryFolder.newFolder("files");
        File zip = runtimeZip();
        RuntimeInstaller.install(base, new FileInputStream(zip), zip.length(), "test", (a, b) -> {
        });

        File bin = new File(base, "opencode/opencode2.bin");
        assertTrue("pré-condição: remover bit de execução", bin.setExecutable(false, false));
        assertFalse(bin.canExecute());

        RuntimeInstaller.InstallResult result = RuntimeInstaller.install(
                base, new FileInputStream(zip), zip.length(), "test", (a, b) -> {
                });

        assertEquals(RuntimeInstaller.InstallResult.UPTODATE, result);
        assertExecutable(base, "opencode/opencode2.bin");
    }

    private static void assertExecutable(File base, String relativePath) {
        assertTrue(relativePath + " deve ser executável", new File(base, relativePath).canExecute());
    }

    private File runtimeZip() throws IOException {
        File zip = temporaryFolder.newFile("runtime.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            entry(out, "opencode/opencode2", "#!/system/bin/sh\n");
            entry(out, "opencode/opencode2.bin", "bin");
            entry(out, "bin/rg", "rg");
        }
        return zip;
    }

    private static void entry(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
