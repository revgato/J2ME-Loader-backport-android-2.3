/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package javax.microedition.shell;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class MicroActivityThemeTest {
    @Test
    public void usesDarkTextForLightweightJ2meScreens() throws IOException {
        String manifest = readSourceFile("src/main/AndroidManifest.xml");
        String styles = readSourceFile("src/main/res/values/styles.xml");
        String layout = readSourceFile("src/main/res/layout/activity_micro.xml");

        assertTrue(manifest.contains(
                "android:name=\"javax.microedition.shell.MicroActivity\"\n"
                        + "            android:theme=\"@style/AppTheme.Midlet\""));
        assertTrue(styles.contains(
                "<style name=\"AppTheme.Midlet\" parent=\"android:style/Theme.Light.NoTitleBar\">"));
        assertTrue(styles.contains(
                "<item name=\"android:colorBackground\">@color/legacy_screen_background</item>"));
        assertTrue(styles.contains(
                "<item name=\"android:textColorPrimary\">@color/legacy_screen_text_primary</item>"));
        assertTrue(styles.contains(
                "<item name=\"android:textColorSecondary\">@color/legacy_screen_text_secondary</item>"));
        assertTrue(layout.contains("android:textColor=\"@android:color/white\""));
    }

    private static String readSourceFile(String relativePath) throws IOException {
        File file = new File(relativePath);
        if (!file.isFile()) {
            file = new File("../" + relativePath);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
