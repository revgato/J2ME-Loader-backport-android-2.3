package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LegacyDexFilesTest {
    @Test
    public void missingCountKeepsSingleDexCompatibility() throws Exception {
        File app = tempDirectory();
        try {
            write(new File(app, "converted.dex"), "dex\n035\0");
            assertEquals(1, LegacyDexFiles.list(app).size());
        } finally {
            delete(app);
        }
    }

    @Test
    public void countRequiresEveryOrderedPart() throws Exception {
        File app = tempDirectory();
        try {
            write(new File(app, "converted.dex"), "dex\n035\0");
            write(new File(app, "converted.dex.conf"),
                    "J2ME-Loader-Dex-Count: 2\n");
            try {
                LegacyDexFiles.list(app);
                fail("missing second part should be rejected");
            } catch (java.io.IOException expected) {
                // expected
            }
            write(new File(app, "converted.2.dex"), "dex\n035\0");
            assertEquals(2, LegacyDexFiles.list(app).size());
        } finally {
            delete(app);
        }
    }

    private static File tempDirectory() throws Exception {
        File file = File.createTempFile("legacy-dex-files-", "");
        if (!file.delete() || !file.mkdirs()) throw new AssertionError("temp directory");
        return file;
    }

    private static void write(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        output.write(value.getBytes("ISO-8859-1"));
        output.close();
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
