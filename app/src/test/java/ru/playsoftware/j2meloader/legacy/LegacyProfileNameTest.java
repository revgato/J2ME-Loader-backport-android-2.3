package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LegacyProfileNameTest {
    @Test
    public void trimsUnicodeNameAndAllowsSpaces() {
        assertEquals("Ninja 你好", LegacyProfileName.normalize("  Ninja 你好  "));
    }

    @Test
    public void rejectsTraversalSeparatorsAndControlCharacters() {
        String[] invalid = {"", "   ", ".", "..", "a/b", "a\\b", "a\n b"};
        for (String value : invalid) {
            try {
                LegacyProfileName.normalize(value);
                fail("name should be rejected: " + value);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void acceptsOnlyDirectChildrenOfRoot() throws Exception {
        File root = tempDirectory();
        assertTrue(LegacyProfileName.isChildOf(root, new File(root, "t9")));
        assertFalse(LegacyProfileName.isChildOf(root, new File(root, "../outside")));
        delete(root);
    }

    @Test
    public void comparesNamesWithoutCase() {
        assertTrue(LegacyProfileName.isSame("T9", "t9"));
        assertFalse(LegacyProfileName.isSame("t9", "t10"));
    }

    private static File tempDirectory() throws Exception {
        File root = File.createTempFile("legacy-profile-name-", "");
        if (!root.delete() || !root.mkdirs()) {
            throw new AssertionError("could not create temp directory");
        }
        return root;
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        file.delete();
    }
}
