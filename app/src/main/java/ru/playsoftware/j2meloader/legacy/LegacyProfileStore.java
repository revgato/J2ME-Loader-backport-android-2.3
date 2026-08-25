/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;
import java.io.IOException;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

/** File-backed profile operations shared by the legacy launcher and MIDlet loader. */
public final class LegacyProfileStore {
    private LegacyProfileStore() {
    }

    public static boolean applyDefaultIfMissing(File gameConfigDir, String profileName)
            throws IOException {
        if (gameConfigDir == null || hasAnyConfig(gameConfigDir)) {
            return false;
        }
        File configsRoot = new File(Config.getConfigsDir());
        if (!LegacyProfileName.isChildOf(configsRoot, gameConfigDir)) {
            return false;
        }
        if (!LegacyProfileName.isValid(profileName)) {
            return false;
        }
        File profilesRoot = new File(Config.getProfilesDir());
        File profileDir = new File(profilesRoot, profileName);
        if (!LegacyProfileName.isChildOf(profilesRoot, profileDir)
                || !profileDir.isDirectory()) {
            return false;
        }
        ProfileModel profile = ProfilesManager.loadConfig(profileDir);
        if (profile == null) {
            return false;
        }
        if (!gameConfigDir.exists() && !gameConfigDir.mkdirs()) {
            throw new IOException("Cannot create game config directory: " + gameConfigDir);
        }
        profile.dir = gameConfigDir;
        if (!ProfilesManager.saveConfig(profile)) {
            throw new IOException("Cannot save default profile to " + gameConfigDir);
        }
        copyKeyboardLayout(profileDir, gameConfigDir);
        return true;
    }

    public static boolean hasAnyConfig(File configDir) {
        return configDir != null && (new File(configDir, Config.MIDLET_CONFIG_FILE).isFile()
                || new File(configDir, "config.xml").isFile());
    }

    public static void copyKeyboardLayout(File sourceDir, File targetDir) throws IOException {
        File source = new File(sourceDir, Config.MIDLET_KEY_LAYOUT_FILE);
        if (!source.isFile()) {
            return;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create target directory: " + targetDir);
        }
        LegacyFileStore.copy(source, new File(targetDir, Config.MIDLET_KEY_LAYOUT_FILE));
    }

    public static Profile findProfile(String name) {
        if (!LegacyProfileName.isValid(name)) {
            return null;
        }
        File root = new File(Config.getProfilesDir());
        File dir = new File(root, name);
        if (!LegacyProfileName.isChildOf(root, dir) || !dir.isDirectory()) {
            return null;
        }
        return new Profile(name);
    }
}
