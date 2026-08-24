package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LegacyInstallerTest {
    @Test
    public void installsAndUpdatesWithoutTouchingRms() throws Exception {
        File root = tempDirectory();
        File jar = createJar("Demo", "Fixture", "1.0");
        LegacyInstaller installer = new LegacyInstaller(root, new FakeConverter());
        InstallResult first = installer.install(jar);
        assertEquals(InstallResult.Status.INSTALLED, first.getStatus());
        assertTrue(readText(new File(first.getAppDirectory(), "converted.dex.conf"))
                .indexOf("MIDlet-1:") >= 0);
        File rms = new File(new File(root, "data"), first.getAppDirectory().getName() + "/rms.bin");
        assertTrue(rms.getParentFile().mkdirs());
        write(rms, "keep");

        File update = createJar("Demo", "Fixture", "2.0");
        InstallResult second = installer.install(update);
        assertEquals(InstallResult.Status.UPDATED, second.getStatus());
        assertEquals("keep", new String(read(rms), "UTF-8"));
        delete(root);
        jar.delete();
        update.delete();
    }

    @Test
    public void rejectsNetworkJadBeforeConversion() throws Exception {
        File root = tempDirectory();
        File jad = new File(root, "demo.jad");
        write(jad, "MIDlet-Jar-URL: https://example.invalid/demo.jar\n");
        InstallResult result = new LegacyInstaller(root, new FakeConverter()).install(jad);
        assertEquals(InstallResult.Status.FAILED, result.getStatus());
        assertTrue(new File(root, "converted").list() == null);
        delete(root);
    }

    @Test
    public void rejectsNonNetworkJadSchemesToo() throws Exception {
        File root = tempDirectory();
        File jad = new File(root, "demo.jad");
        write(jad, "MIDlet-Jar-URL: file:demo.jar\n");
        InstallResult result = new LegacyInstaller(root, new FakeConverter()).install(jad);
        assertEquals(InstallResult.Status.FAILED, result.getStatus());
        delete(root);
    }

    @Test
    public void rejectsMalformedJarWithoutPublishing() throws Exception {
        File root = tempDirectory();
        File jar = new File(root, "broken.jar");
        write(jar, "not a zip");
        InstallResult result = new LegacyInstaller(root, new FakeConverter()).install(jar);
        assertEquals(InstallResult.Status.FAILED, result.getStatus());
        assertTrue(new File(root, "converted").list() == null);
        delete(root);
    }

    @Test
    public void installsJadOnlyWhenJarIsBesideIt() throws Exception {
        File root = tempDirectory();
        File jar = new File(root, "demo.jar");
        copy(createJar("JadDemo", "Fixture", "1.0"), jar);
        File jad = new File(root, "demo.jad");
        write(jad, "MIDlet-Jar-URL: demo.jar\n");
        InstallResult result = new LegacyInstaller(root, new FakeConverter()).install(jad);
        assertEquals(InstallResult.Status.INSTALLED, result.getStatus());
        delete(root);
    }

    private static File createJar(String name, String vendor, String version) throws Exception {
        File jar = File.createTempFile("legacy-installer-", ".jar");
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("MIDlet-Name", name);
        attrs.putValue("MIDlet-Vendor", vendor);
        attrs.putValue("MIDlet-Version", version);
        attrs.putValue("MIDlet-1", name + ",/icon.png,com.example.Main");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar));
        zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
        manifest.write(zip);
        zip.closeEntry();
        zip.close();
        return jar;
    }

    private static void write(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        output.write(value.getBytes("UTF-8"));
        output.close();
    }

    private static void copy(File source, File destination) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(source);
        FileOutputStream output = new FileOutputStream(destination);
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } finally {
            input.close();
            output.close();
            source.delete();
        }
    }

    private static byte[] read(File file) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        input.read(data);
        input.close();
        return data;
    }

    private static String readText(File file) throws Exception {
        return new String(read(file), "UTF-8");
    }

    private static File tempDirectory() throws Exception {
        File dir = File.createTempFile("legacy-installer-root-", "");
        if (!dir.delete() || !dir.mkdirs()) throw new AssertionError("temp directory");
        return dir;
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }

    private static final class FakeConverter implements LegacyInstaller.DexConverter {
        @Override public void convert(File jar, File dex) throws java.io.IOException {
            FileOutputStream output = new FileOutputStream(dex);
            output.write(new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0});
            output.close();
        }
    }
}
