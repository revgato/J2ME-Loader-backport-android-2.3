package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.fail;

public class LegacyArchiveValidatorTest {
    @Test
    public void acceptsSmallSafeArchive() throws Exception {
        File archive = archive(new Entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));
        LegacyArchiveValidator.validate(archive);
        archive.delete();
    }

    @Test
    public void rejectsParentTraversal() throws Exception {
        rejects(archive(new Entry("../outside.txt", "x")));
    }

    @Test
    public void rejectsAbsoluteAndWindowsTraversal() throws Exception {
        rejects(archive(new Entry("/outside.txt", "x")));
        rejects(archive(new Entry("..\\outside.txt", "x")));
        rejects(archive(new Entry("./outside.txt", "x")));
        rejects(archive(new Entry("dir//outside.txt", "x")));
    }

    @Test
    public void rejectsTooManyEntries() throws Exception {
        File file = File.createTempFile("legacy-many-", ".jar");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (int i = 0; i < LegacyArchiveValidator.MAX_ENTRIES + 1; i++) {
                zip.putNextEntry(new ZipEntry("e" + i));
                zip.write(1);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        rejects(file);
    }

    @Test
    public void rejectsDeclaredUncompressedLimit() throws Exception {
        File file = File.createTempFile("legacy-large-", ".jar");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            ZipEntry entry = new ZipEntry("large");
            entry.setMethod(ZipEntry.STORED);
            byte[] data = new byte[1024 * 1024];
            // A normal test JVM cannot create a 128 MiB fixture cheaply; repeat enough entries
            // to cross the same stream accounting path without relying on central-directory lies.
            for (int i = 0; i < 129; i++) {
                entry = new ZipEntry("large" + i);
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        rejects(file);
    }

    private static void rejects(File file) throws IOException {
        try {
            LegacyArchiveValidator.validate(file);
            fail("archive should be rejected: " + file);
        } catch (IOException expected) {
            // expected
        } finally {
            file.delete();
        }
    }

    private static File archive(Entry... entries) throws IOException {
        File file = File.createTempFile("legacy-archive-", ".jar");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (Entry value : entries) {
                zip.putNextEntry(new ZipEntry(value.name));
                zip.write(value.data.getBytes("UTF-8"));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return file;
    }

    private static final class Entry {
        final String name;
        final String data;

        Entry(String name, String data) {
            this.name = name;
            this.data = data;
        }
    }
}
