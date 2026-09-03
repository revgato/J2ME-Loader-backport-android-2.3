package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    @Test
    public void deletesGameDirectoryRecursivelyAndRemovesItFromCatalog() throws Exception {
        File root = tempDirectory();
        try {
            File game = new File(new File(root, "converted"), "Demo");
            if (!game.mkdirs()) {
                throw new AssertionError("could not create fixture");
            }
            writeDescriptor(game, "MIDlet-Name: Demo\n");
            File nested = new File(game, "nested/cache.bin");
            if (!nested.getParentFile().mkdirs()) {
                throw new AssertionError("could not create nested fixture");
            }
            writeBytes(nested, "cache");
            File config = new File(root, "configs/Demo");
            File data = new File(root, "data/Demo");
            if (!config.mkdirs() || !data.mkdirs()) {
                throw new AssertionError("could not create sibling fixture");
            }

            FileLegacyAppCatalog catalog = new FileLegacyAppCatalog(root);
            java.util.List<LegacyAppCatalog.Game> games = catalog.scan();
            catalog.delete(games.get(0));

            assertFalse(game.exists());
            assertTrue(config.exists());
            assertTrue(data.exists());
            assertEquals(0, catalog.scan().size());
        } finally {
            delete(root);
        }
    }

    @Test
    public void refusesToDeleteDirectoryOutsideConverted() throws Exception {
        File root = tempDirectory();
        try {
            File converted = new File(root, "converted");
            File outside = new File(root, "outside");
            if (!converted.mkdirs() || !outside.mkdirs()) {
                throw new AssertionError("could not create fixture");
            }
            FileLegacyAppCatalog catalog = new FileLegacyAppCatalog(root);
            LegacyAppCatalog.Game game = new LegacyAppCatalog.Game(
                    outside.getName(), "Outside", "", "", outside);

            try {
                catalog.delete(game);
                fail("expected a path validation failure");
            } catch (IOException expected) {
                assertTrue(outside.exists());
            }
        } finally {
            delete(root);
        }
    }

    @Test
    public void refusesToDeleteConvertedDirectoryItself() throws Exception {
        File root = tempDirectory();
        try {
            File converted = new File(root, "converted");
            if (!converted.mkdirs()) {
                throw new AssertionError("could not create fixture");
            }
            FileLegacyAppCatalog catalog = new FileLegacyAppCatalog(root);
            LegacyAppCatalog.Game game = new LegacyAppCatalog.Game(
                    converted.getName(), "Converted", "", "", converted);

            try {
                catalog.delete(game);
                fail("expected a path validation failure");
            } catch (IOException expected) {
                assertTrue(converted.exists());
            }
        } finally {
            delete(root);
        }
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

    private static void writeBytes(File file, String contents) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
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
