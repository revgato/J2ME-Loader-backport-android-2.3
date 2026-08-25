package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyFileStoreTest {
    @Test
    public void writesUtf8AndReplacesExistingFileWithoutBackup() throws Exception {
        File root = Files.createTempDirectory("legacy-file-store-").toFile();
        File target = new File(root, "config.json");
        LegacyFileStore.writeUtf8(target, "Hồ sơ 東京");
        LegacyFileStore.writeUtf8(target, "updated");

        assertEquals("updated", new String(Files.readAllBytes(target.toPath()), Charset.forName("UTF-8")));
        assertFalse(new File(root, "config.json.tmp").exists());
        assertFalse(new File(root, "config.json.bak").exists());
        delete(root);
    }

    @Test
    public void copiesOnlyExistingSourceAndPublishesCompleteBytes() throws Exception {
        File root = Files.createTempDirectory("legacy-file-store-copy-").toFile();
        File source = new File(root, "source");
        File target = new File(root, "nested/target");
        Files.write(source.toPath(), "payload".getBytes("UTF-8"));

        LegacyFileStore.copy(source, target);

        assertTrue(target.isFile());
        assertEquals("payload", new String(Files.readAllBytes(target.toPath()), "UTF-8"));
        assertFalse(new File(target.getParentFile(), "target.tmp").exists());
        delete(root);
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
