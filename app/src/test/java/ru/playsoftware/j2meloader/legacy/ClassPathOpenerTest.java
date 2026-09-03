package ru.playsoftware.j2meloader.legacy;

import com.android.dx.cf.direct.ClassPathOpener;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassPathOpenerTest {
    @Test
    public void readsDeclaredEntryWithoutASecondSizedCopy() throws Exception {
        File archive = File.createTempFile("class-path-opener-", ".jar");
        final int expected = 84704;
        try {
            ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive));
            output.putNextEntry(new ZipEntry("Game.class"));
            for (int i = 0; i < expected; i++) output.write(i & 0xff);
            output.closeEntry();
            output.close();

            final int[] length = new int[1];
            ClassPathOpener opener = new ClassPathOpener(archive.getAbsolutePath(), true,
                    new ClassPathOpener.FileNameFilter() {
                        @Override public boolean accept(String path) { return true; }
                    }, new ClassPathOpener.Consumer() {
                        @Override public boolean processFileBytes(String name, long modified,
                                byte[] bytes) {
                            length[0] = bytes.length;
                            return true;
                        }
                        @Override public void onException(Exception ex) { throw new AssertionError(ex); }
                        @Override public void onProcessArchiveStart(File file) { }
                    });
            assertTrue(opener.process());
            assertEquals(expected, length[0]);
        } finally {
            archive.delete();
        }
    }
}
