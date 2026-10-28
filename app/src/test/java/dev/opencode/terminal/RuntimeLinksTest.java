package dev.opencode.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RuntimeLinksTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsRgSymlinkToNativeBinary() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File nativeDir = temporaryFolder.newFolder("native");
        Path target = new File(nativeDir, "librg.so").toPath();
        Files.write(target, new byte[] {0x7f, 'E', 'L', 'F'});

        RuntimeLinks.ensureRipgrepLink(files, nativeDir);

        Path link = new File(files, "bin/rg").toPath();
        assertTrue("rg deve ser um symlink", Files.isSymbolicLink(link));
        assertEquals(target, Files.readSymbolicLink(link));
    }

    @Test
    public void isIdempotent() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File nativeDir = temporaryFolder.newFolder("native");
        Files.write(new File(nativeDir, "librg.so").toPath(), new byte[] {0x7f});

        RuntimeLinks.ensureRipgrepLink(files, nativeDir);
        RuntimeLinks.ensureRipgrepLink(files, nativeDir);

        Path link = new File(files, "bin/rg").toPath();
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    public void replacesStaleRegularFile() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File nativeDir = temporaryFolder.newFolder("native");
        Files.write(new File(nativeDir, "librg.so").toPath(), new byte[] {0x7f});
        Path link = new File(files, "bin/rg").toPath();
        Files.createDirectories(link.getParent());
        Files.write(link, new byte[] {'o', 'l', 'd'});
        assertFalse(Files.isSymbolicLink(link));

        RuntimeLinks.ensureRipgrepLink(files, nativeDir);

        assertTrue(Files.isSymbolicLink(link));
    }

    @Test(expected = IOException.class)
    public void missingNativeRgFails() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File nativeDir = temporaryFolder.newFolder("native");

        RuntimeLinks.ensureRipgrepLink(files, nativeDir);
    }
}
