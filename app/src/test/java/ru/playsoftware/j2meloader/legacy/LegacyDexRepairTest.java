package ru.playsoftware.j2meloader.legacy;

import com.android.dex.ClassDef;
import com.android.dex.Dex;
import com.android.dx.command.dexer.Main;

import com.example.legacyrepair.MissingFixture;
import com.example.legacyrepair.PrimaryFixture;
import com.example.legacyrepair.AlternateFixture;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LegacyDexRepairTest {
    @Test
    public void repairsMisnamedClassWithoutChangingPrimaryDex() throws Exception {
        File root = tempDirectory();
        try {
            File app = new File(root, "game");
            assertTrue(app.mkdirs());
            File primary = new File(app, "converted.dex");
            File res = new File(app, "res.jar");
            buildDex(primary, internalName(PrimaryFixture.class) + ".class", resource(PrimaryFixture.class));
            writeJar(res, "r.class", resource(MissingFixture.class));
            byte[] primaryBefore = readFile(primary);
            String hashBefore = sha256(primaryBefore);

            LegacyDexRepair.Result result = LegacyDexRepair.prepare(app, new File(root, "scratch"));

            assertEquals(LegacyDexRepair.Status.READY, result.getStatus());
            assertEquals(1, result.getClassCount());
            assertNotNull(result.getCompatDex());
            assertTrue(result.getCompatDex().isFile());
            assertArrayEquals(primaryBefore, readFile(primary));
            assertEquals(hashBefore, sha256(readFile(primary)));
            assertTrue(containsClass(result.getCompatDex(), internalName(MissingFixture.class)));
        } finally {
            delete(root);
        }
    }

    @Test
    public void reusesAndInvalidatesMarkerCache() throws Exception {
        File root = tempDirectory();
        try {
            File app = new File(root, "game");
            assertTrue(app.mkdirs());
            File primary = new File(app, "converted.dex");
            File res = new File(app, "res.jar");
            buildDex(primary, internalName(PrimaryFixture.class) + ".class", resource(PrimaryFixture.class));
            writeJar(res, "r.class", resource(MissingFixture.class));

            LegacyDexRepair.Result first = LegacyDexRepair.prepare(app, new File(root, "scratch"));
            long markerTime = new File(app, "converted.compat.marker").lastModified();
            LegacyDexRepair.Result second = LegacyDexRepair.prepare(app, new File(root, "scratch"));
            assertEquals(LegacyDexRepair.Status.READY, second.getStatus());
            assertEquals(first.getCompatDex().getAbsolutePath(), second.getCompatDex().getAbsolutePath());
            assertEquals(markerTime, new File(app, "converted.compat.marker").lastModified());

            buildDex(primary, internalName(AlternateFixture.class) + ".class",
                    resource(AlternateFixture.class));
            LegacyDexRepair.Result invalidated = LegacyDexRepair.prepare(app, new File(root, "scratch"));
            assertEquals(LegacyDexRepair.Status.READY, invalidated.getStatus());
            assertTrue(new File(app, "converted.compat.marker").lastModified() >= markerTime);
        } finally {
            delete(root);
        }
    }

    @Test
    public void rejectsDuplicateAndProtectedClasses() throws Exception {
        assertTrue(LegacyDexRepair.isProtectedNamespace("java/lang/String"));
        assertTrue(LegacyDexRepair.isProtectedNamespace("org/microemu/SomeClass"));
        assertFalse(LegacyDexRepair.isProtectedNamespace("com/example/GameClass"));

        File root = tempDirectory();
        try {
            File app = new File(root, "game");
            assertTrue(app.mkdirs());
            File primary = new File(app, "converted.dex");
            File res = new File(app, "res.jar");
            buildDex(primary, internalName(PrimaryFixture.class) + ".class", resource(PrimaryFixture.class));
            writeJar(res, "one.class", resource(MissingFixture.class),
                    "two.class", resource(MissingFixture.class));
            try {
                LegacyDexRepair.prepare(app, new File(root, "scratch"));
                fail("duplicate class should not be published");
            } catch (IOException expected) {
                assertFalse(new File(app, "converted.compat.dex").exists());
            }
        } finally {
            delete(root);
        }
    }

    @Test
    public void ignoresAlreadyNamedClassesAndRejectsUnsafeEntries() throws Exception {
        File root = tempDirectory();
        try {
            File app = new File(root, "game");
            assertTrue(app.mkdirs());
            File primary = new File(app, "converted.dex");
            File res = new File(app, "res.jar");
            buildDex(primary, internalName(PrimaryFixture.class) + ".class", resource(PrimaryFixture.class));
            writeJar(res, internalName(MissingFixture.class) + ".class", resource(MissingFixture.class));
            LegacyDexRepair.Result none = LegacyDexRepair.prepare(app, new File(root, "scratch"));
            assertEquals(LegacyDexRepair.Status.NONE, none.getStatus());
            assertFalse(new File(app, "converted.compat.dex").exists());

            writeJar(res, "java/lang/Fake.class", resource(MissingFixture.class));
            try {
                LegacyDexRepair.prepare(app, new File(root, "scratch"));
                fail("protected namespace should be rejected");
            } catch (IOException expected) {
                // expected
            }
            writeJar(res, "../escape.class", resource(MissingFixture.class));
            try {
                LegacyDexRepair.prepare(app, new File(root, "scratch"));
                fail("unsafe path should be rejected");
            } catch (IOException expected) {
                // expected
            }
        } finally {
            delete(root);
        }
    }

    private static void buildDex(File dex, String entryName, byte[] bytes) throws Exception {
        File jar = new File(dex.getParentFile(), "primary.jar");
        writeJar(jar, entryName, bytes);
        Main.main(LegacyInstaller.dexArguments(jar, dex));
        assertTrue(dex.isFile());
    }

    private static byte[] resource(Class<?> type) throws IOException {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        InputStream input = LegacyDexRepairTest.class.getResourceAsStream(path);
        if (input == null) throw new IOException("missing fixture: " + path);
        try {
            return readAll(input);
        } finally {
            input.close();
        }
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static boolean containsClass(File dex, String internalName) throws IOException {
        Dex parsed = new Dex(dex);
        String descriptor = "L" + internalName + ";";
        for (ClassDef classDef : parsed.classDefs()) {
            if (descriptor.equals(parsed.typeNames().get(classDef.getTypeIndex()))) return true;
        }
        return false;
    }

    private static void writeJar(File file, Object... entries) throws Exception {
        ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file));
        try {
            for (int i = 0; i < entries.length; i += 2) {
                output.putNextEntry(new ZipEntry((String) entries[i]));
                output.write((byte[]) entries[i + 1]);
                output.closeEntry();
            }
        } finally {
            output.close();
        }
    }

    private static byte[] readFile(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        try {
            return readAll(input);
        } finally {
            input.close();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static File tempDirectory() throws IOException {
        File root = File.createTempFile("legacy-dex-repair-", "");
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
