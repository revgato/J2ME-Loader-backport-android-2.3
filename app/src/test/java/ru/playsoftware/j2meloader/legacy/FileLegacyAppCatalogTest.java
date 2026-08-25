package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FileLegacyAppCatalogTest {
    @Test
    public void scansConvertedDescriptorsWithoutDatabase() throws Exception {
        File root = tempDirectory();
        File game = new File(new File(root, "converted"), "Demo");
        if (!game.mkdirs()) {
            throw new AssertionError("could not create fixture");
        }
        File descriptor = new File(game, "converted.dex.conf");
        FileOutputStream output = new FileOutputStream(descriptor);
        output.write(("MIDlet-Name: Demo\nMIDlet-Vendor: Fixture\nMIDlet-Version: 1.0\n")
                .getBytes("UTF-8"));
        output.close();

        java.util.List<LegacyAppCatalog.Game> games = new FileLegacyAppCatalog(root).scan();
        assertEquals(1, games.size());
        assertEquals("Demo", games.get(0).getName());
        assertEquals("Fixture", games.get(0).getVendor());
        assertNull(games.get(0).getIconEntry());
        delete(root);
    }

    @Test
    public void readsExplicitIconPathAndRemovesLeadingSlash() throws Exception {
        File root = tempDirectory();
        File game = new File(new File(root, "converted"), "Demo");
        if (!game.mkdirs()) {
            throw new AssertionError("could not create fixture");
        }
        writeDescriptor(game, "MIDlet-Name: Demo\nMIDlet-Icon: /res/icon.png\n"
                + "MIDlet-1: Demo, /wrong.png,com.example.Main\n");

        java.util.List<LegacyAppCatalog.Game> games = new FileLegacyAppCatalog(root).scan();

        assertEquals("res/icon.png", games.get(0).getIconEntry());
        delete(root);
    }

    @Test
    public void fallsBackToIconFromFirstMidletDeclaration() throws Exception {
        File root = tempDirectory();
        File game = new File(new File(root, "converted"), "Demo");
        if (!game.mkdirs()) {
            throw new AssertionError("could not create fixture");
        }
        writeDescriptor(game, "MIDlet-Name: Demo\nMIDlet-1: Demo, /icon.png,com.example.Main\n");

        java.util.List<LegacyAppCatalog.Game> games = new FileLegacyAppCatalog(root).scan();

        assertEquals("icon.png", games.get(0).getIconEntry());
        delete(root);
    }

    private static File tempDirectory() throws Exception {
        File root = File.createTempFile("legacy-catalog-", "");
        if (!root.delete() || !root.mkdirs()) {
            throw new AssertionError("could not create temp directory");
        }
        return root;
    }

    private static void writeDescriptor(File game, String contents) throws Exception {
        File descriptor = new File(game, "converted.dex.conf");
        FileOutputStream output = new FileOutputStream(descriptor);
        try {
            output.write(contents.getBytes("UTF-8"));
        } finally {
            output.close();
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        file.delete();
    }
}
