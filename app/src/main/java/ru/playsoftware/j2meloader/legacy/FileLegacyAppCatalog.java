/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Reads the converted directory directly; no database or reactive dependency is required. */
public final class FileLegacyAppCatalog implements LegacyAppCatalog {
    private final File convertedDirectory;

    public FileLegacyAppCatalog(File emulatorDirectory) {
        if (emulatorDirectory == null) {
            throw new NullPointerException("emulatorDirectory");
        }
        convertedDirectory = new File(emulatorDirectory, "converted");
    }

    public File getConvertedDirectory() {
        return convertedDirectory;
    }

    @Override
    public List<Game> scan() throws IOException {
        ArrayList<Game> games = new ArrayList<Game>();
        File[] children = convertedDirectory.listFiles();
        if (children == null) {
            return games;
        }
        for (File child : children) {
            if (!child.isDirectory() || child.getName().startsWith(".")) {
                continue;
            }
            File descriptor = new File(child, "converted.dex.conf");
            if (!descriptor.isFile()) {
                continue;
            }
            String name = read(descriptor, "MIDlet-Name");
            if (name == null || name.length() == 0) {
                continue;
            }
            String vendor = valueOrEmpty(read(descriptor, "MIDlet-Vendor"));
            String version = valueOrEmpty(read(descriptor, "MIDlet-Version"));
            String iconEntry = iconEntry(descriptor);
            games.add(new Game(child.getName(), name, vendor, version, iconEntry, child));
        }
        Collections.sort(games, new Comparator<Game>() {
            @Override
            public int compare(Game left, Game right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return games;
    }

    private static String iconEntry(File descriptor) throws IOException {
        String icon = normalizeIconEntry(read(descriptor, "MIDlet-Icon"));
        if (icon == null) {
            String firstMidlet = read(descriptor, "MIDlet-1");
            if (firstMidlet != null) {
                int firstComma = firstMidlet.indexOf(',');
                if (firstComma >= 0) {
                    int secondComma = firstMidlet.indexOf(',', firstComma + 1);
                    int end = secondComma >= 0 ? secondComma : firstMidlet.length();
                    icon = normalizeIconEntry(firstMidlet.substring(firstComma + 1, end));
                }
            }
        }
        return icon;
    }

    private static String normalizeIconEntry(String icon) {
        if (icon == null) {
            return null;
        }
        icon = icon.trim();
        while (icon.startsWith("/")) {
            icon = icon.substring(1);
        }
        return icon.length() == 0 ? null : icon;
    }

    private static String read(File file, String wanted) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                if (wanted.equals(key)) {
                    return line.substring(separator + 1).trim();
                }
            }
        } finally {
            reader.close();
        }
        return null;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
