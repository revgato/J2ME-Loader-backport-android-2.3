package javax.microedition.shell;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DexJarCacheTest {
    @Test
    public void wrapsRawDexAsClassesEntry() throws Exception {
        File root = tempDirectory();
        File dex = new File(root, "converted.dex");
        byte[] bytes = new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0, 1, 2, 3, 4};
        write(dex, bytes);
        File cache = new File(root, "cache");

        File jar = DexJarCache.create(dex, cache);

        assertTrue(jar.isFile());
        ZipFile zip = new ZipFile(jar);
        try {
            ZipEntry entry = zip.getEntry("classes.dex");
            assertTrue(entry != null);
            assertArrayEquals(bytes, read(zip.getInputStream(entry), (int) entry.getSize()));
            assertEquals(1, zip.size());
        } finally {
            zip.close();
            delete(root);
        }
    }

    @Test
    public void rejectsNonDexInputWithoutPublishingCache() throws Exception {
        File root = tempDirectory();
        File dex = new File(root, "converted.dex");
        write(dex, new byte[]{'n', 'o', 't', '-', 'd', 'e', 'x'});
        File cache = new File(root, "cache");

        try {
            DexJarCache.create(dex, cache);
            fail("invalid DEX should be rejected");
        } catch (java.io.IOException expected) {
            assertFalse(cache.exists());
        } finally {
            delete(root);
        }
    }

    private static byte[] read(java.io.InputStream input, int size) throws Exception {
        byte[] bytes = new byte[size];
        int offset = 0;
        while (offset < size) {
            int count = input.read(bytes, offset, size - offset);
            if (count < 0) throw new AssertionError("unexpected end of entry");
            offset += count;
        }
        input.close();
        return bytes;
    }

    private static void write(File file, byte[] bytes) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static File tempDirectory() throws Exception {
        File root = File.createTempFile("dex-cache-test-", "");
        if (!root.delete() || !root.mkdirs()) throw new AssertionError("temp directory");
        return root;
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
