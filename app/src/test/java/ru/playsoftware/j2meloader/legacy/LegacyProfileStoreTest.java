/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import ru.playsoftware.j2meloader.config.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyProfileStoreTest {
    @Test
    public void existingJsonOrXmlAlwaysBlocksDefaultBootstrap() throws Exception {
        File root = Files.createTempDirectory("legacy-bootstrap-").toFile();
        File game = new File(root, "game");
        assertFalse(LegacyProfileStore.hasAnyConfig(game));
        assertTrue(game.mkdirs());
        Files.write(new File(game, Config.MIDLET_CONFIG_FILE).toPath(), "{}".getBytes("UTF-8"));
        assertTrue(LegacyProfileStore.hasAnyConfig(game));
        new File(game, Config.MIDLET_CONFIG_FILE).delete();
        Files.write(new File(game, "config.xml").toPath(), "<map/>".getBytes("UTF-8"));
        assertTrue(LegacyProfileStore.hasAnyConfig(game));
        delete(root);
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
