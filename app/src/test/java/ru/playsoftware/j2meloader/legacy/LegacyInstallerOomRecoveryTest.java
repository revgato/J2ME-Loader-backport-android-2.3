package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Regression coverage for adaptive low-memory conversion. */
public class LegacyInstallerOomRecoveryTest {
    private static final byte[] DEX_MAGIC = new byte[]{
            'd', 'e', 'x', '\n', '0', '3', '5', 0
    };

    @Test
    public void payloadBetweenNewAndOldBatchLimitsIsSplit() throws Exception {
        File root = tempDirectory();
        File jar = createJarWithClasses("SizedDemo", new int[]{160 * 1024, 160 * 1024});
        RecordingConverter converter = new RecordingConverter();
        try {
            long classBytes = LegacyArchiveValidator.inspect(jar).getClassBytes();
            assertTrue("fixture must exceed the new 256 KiB limit", classBytes > 256 * 1024L);
            assertTrue("fixture must remain below the old 512 KiB limit", classBytes < 512 * 1024L);

            InstallResult result = new LegacyInstaller(root, converter).install(jar);

            assertEquals(InstallResult.Status.INSTALLED, result.getStatus());
            assertEquals(Arrays.asList(1, 1), converter.successfulBatchSizes);
            assertEquals(Arrays.asList("com/example/SingleClass0.class"),
                    converter.successfulBatchEntries.get(0));
            assertEquals(Arrays.asList("com/example/SingleClass1.class"),
                    converter.successfulBatchEntries.get(1));
            assertEquals("J2ME-Loader-Dex-Count: 2", findLine(
                    new File(result.getAppDirectory(), "converted.dex.conf"),
                    "J2ME-Loader-Dex-Count:"));
            assertDex035(new File(result.getAppDirectory(), "converted.dex"));
            assertDex035(new File(result.getAppDirectory(), "converted.2.dex"));
        } finally {
            delete(root);
            jar.delete();
        }
    }

    @Test
    public void outOfMemoryBatchIsSplitAndProgressCommitsOnlyAfterSuccess() throws Exception {
        File root = tempDirectory();
        File jar = createJarWithClasses("AdaptiveDemo", new int[]{16, 16, 16, 16});
        OomSplittingConverter converter = new OomSplittingConverter();
        final List<Integer> progress = new ArrayList<Integer>();
        InstallProgressListener listener = new InstallProgressListener() {
            @Override public void onStage(String stage) {
            }

            @Override public void onProgress(int completed, int total, String className) {
                assertTrue("progress must not exceed total", completed <= total);
                assertTrue("progress must not go backwards", progress.isEmpty()
                        || completed >= progress.get(progress.size() - 1));
                progress.add(completed);
            }

            @Override public void onLog(String level, String message) {
            }
        };
        try {
            InstallResult result = new LegacyInstaller(root, converter).install(jar, listener);

            assertEquals(InstallResult.Status.INSTALLED, result.getStatus());
            // The four-class attempt fails. Only the two successful two-class retries
            // are allowed to advance installer progress.
            assertEquals(Arrays.asList(4, 2, 2), converter.attemptedBatchSizes);
            assertEquals(Arrays.asList(2, 2), converter.successfulBatchSizes);
            assertEquals(Arrays.asList("com/example/SingleClass0.class",
                    "com/example/SingleClass1.class", "com/example/SingleClass2.class",
                    "com/example/SingleClass3.class"), converter.attemptedBatchEntries.get(0));
            assertEquals(Arrays.asList("com/example/SingleClass0.class",
                    "com/example/SingleClass1.class"), converter.attemptedBatchEntries.get(1));
            assertEquals(Arrays.asList("com/example/SingleClass2.class",
                    "com/example/SingleClass3.class"), converter.attemptedBatchEntries.get(2));
            assertEquals(Arrays.asList(2, 4), progress);
            assertEquals("J2ME-Loader-Dex-Count: 2", findLine(
                    new File(result.getAppDirectory(), "converted.dex.conf"),
                    "J2ME-Loader-Dex-Count:"));
            assertNoTemporaryArtifacts(new File(root, "converted"));
            assertDex035(new File(result.getAppDirectory(), "converted.dex"));
            assertDex035(new File(result.getAppDirectory(), "converted.2.dex"));
        } finally {
            delete(root);
            jar.delete();
        }
    }

    @Test
    public void outOfMemorySingleClassFailsWithClassNameAndCleansStaging() throws Exception {
        File root = tempDirectory();
        File jar = createJarWithClasses("SingleClassDemo", new int[]{16});
        SingleClassOomConverter converter = new SingleClassOomConverter();
        try {
            InstallResult result = new LegacyInstaller(root, converter).install(jar);

            assertEquals(InstallResult.Status.FAILED, result.getStatus());
            assertNotNull(result.getMessage());
            assertTrue("failure should identify the unconvertible class",
                    result.getMessage().indexOf("SingleClass") >= 0);
            assertEquals(1, converter.attemptedBatchSizes.size());
            assertTrue(new File(root, "converted").isDirectory());
            assertEquals(0, new File(root, "converted").list().length);
            assertNoTemporaryArtifacts(new File(root, "converted"));
        } finally {
            delete(root);
            jar.delete();
        }
    }

    private static final class RecordingConverter implements LegacyInstaller.DexConverter {
        final List<Integer> successfulBatchSizes = new ArrayList<Integer>();
        final List<List<String>> successfulBatchEntries = new ArrayList<List<String>>();

        @Override public void convert(File jar, File dex) throws IOException {
            List<String> entries = classEntries(jar);
            int count = entries.size();
            successfulBatchSizes.add(count);
            successfulBatchEntries.add(entries);
            writeDex(dex);
        }
    }

    private static final class OomSplittingConverter implements LegacyInstaller.DexConverter {
        final List<Integer> attemptedBatchSizes = new ArrayList<Integer>();
        final List<Integer> successfulBatchSizes = new ArrayList<Integer>();
        final List<List<String>> attemptedBatchEntries = new ArrayList<List<String>>();

        @Override public void convert(File jar, File dex) throws IOException {
            if (dex.exists()) {
                throw new IOException("stale DEX was not removed before retry");
            }
            List<String> entries = classEntries(jar);
            int count = entries.size();
            attemptedBatchSizes.add(count);
            attemptedBatchEntries.add(entries);
            if (count > 2) {
                // Leave a malformed output behind to prove the retry path removes it.
                write(dex, new byte[]{'p', 'a', 'r', 't', 'i', 'a', 'l'});
                throw new OutOfMemoryError("simulated batch OOM");
            }
            successfulBatchSizes.add(count);
            writeDex(dex);
        }
    }

    private static final class SingleClassOomConverter implements LegacyInstaller.DexConverter {
        final List<Integer> attemptedBatchSizes = new ArrayList<Integer>();

        @Override public void convert(File jar, File dex) throws IOException {
            attemptedBatchSizes.add(classEntries(jar).size());
            write(dex, new byte[]{'p', 'a', 'r', 't', 'i', 'a', 'l'});
            throw new OutOfMemoryError("simulated single-class OOM");
        }
    }

    private static File createJarWithClasses(String name, int[] classSizes) throws Exception {
        File jar = File.createTempFile("legacy-installer-oom-", ".jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("MIDlet-Name", name);
        attributes.putValue("MIDlet-Vendor", "Fixture");
        attributes.putValue("MIDlet-Version", "1.0");
        attributes.putValue("MIDlet-1", name + ",/icon.png,com.example.Main");
        JarOutputStream output = new JarOutputStream(new FileOutputStream(jar));
        try {
            output.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            manifest.write(output);
            output.closeEntry();
            for (int i = 0; i < classSizes.length; i++) {
                output.putNextEntry(new JarEntry("com/example/SingleClass" + i + ".class"));
                byte[] bytes = new byte[classSizes[i]];
                for (int j = 0; j < bytes.length; j++) {
                    bytes[j] = (byte) ((j * 31 + i * 17) & 0xff);
                }
                output.write(bytes);
                output.closeEntry();
            }
        } finally {
            output.close();
        }
        return jar;
    }

    private static List<String> classEntries(File jar) throws IOException {
        List<String> result = new ArrayList<String>();
        JarFile file = new JarFile(jar);
        try {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    result.add(entry.getName());
                }
            }
        } finally {
            file.close();
        }
        return result;
    }

    private static void writeDex(File file) throws IOException {
        write(file, DEX_MAGIC);
    }

    private static void write(File file, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static String findLine(File file, String prefix) throws IOException {
        InputStream input = new FileInputStream(file);
        StringBuilder text = new StringBuilder();
        try {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
        } finally {
            input.close();
        }
        String[] lines = text.toString().split("\\n");
        for (String line : lines) {
            if (line.startsWith(prefix)) return line;
        }
        return null;
    }

    private static void assertDex035(File file) throws IOException {
        assertTrue(file.isFile());
        InputStream input = new FileInputStream(file);
        try {
            byte[] magic = new byte[8];
            int read = input.read(magic);
            assertEquals(8, read);
            for (int i = 0; i < DEX_MAGIC.length; i++) assertEquals(DEX_MAGIC[i], magic[i]);
        } finally {
            input.close();
        }
    }

    private static void assertNoTemporaryArtifacts(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            assertFalse("temporary artifact: " + child, child.getName().startsWith(".tmp-"));
            assertFalse("temporary artifact: " + child, child.getName().startsWith(".class-batch-"));
            assertFalse("temporary artifact: " + child, child.getName().startsWith(".backup-"));
            if (child.isDirectory()) assertNoTemporaryArtifacts(child);
        }
    }

    private static File tempDirectory() throws IOException {
        File root = File.createTempFile("legacy-installer-oom-root-", "");
        if (!root.delete() || !root.mkdirs()) throw new IOException("temp directory");
        return root;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
