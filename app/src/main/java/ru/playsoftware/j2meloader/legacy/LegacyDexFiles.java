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
import java.util.List;

/** Resolves the ordered DEX parts in a converted legacy game directory. */
public final class LegacyDexFiles {
    public static final String DEX_COUNT_KEY = "J2ME-Loader-Dex-Count";
    private static final int MAX_PARTS = 4096;

    private LegacyDexFiles() {
    }

    public static List<File> list(File appDir) throws IOException {
        if (appDir == null || !appDir.isDirectory()) {
            throw new IOException("MIDlet application directory is missing");
        }
        int count = readCount(new File(appDir, "converted.dex.conf"));
        List<File> result = new ArrayList<File>(count);
        for (int i = 1; i <= count; i++) {
            File dex = new File(appDir, i == 1 ? "converted.dex" : "converted." + i + ".dex");
            if (!dex.isFile() || !dex.canRead()) {
                throw new IOException("Missing converted DEX part " + i + " of " + count);
            }
            result.add(dex);
        }
        File[] children = appDir.listFiles();
        if (children != null) {
            String prefix = "converted.";
            for (File child : children) {
                String name = child.getName();
                if (!child.isFile() || !name.startsWith(prefix) || !name.endsWith(".dex")
                        || name.length() <= prefix.length() + 4
                        || "converted.compat.dex".equals(name)) {
                    continue;
                }
                String number = name.substring(prefix.length(), name.length() - 4);
                try {
                    int part = Integer.parseInt(number);
                    if (part < 2 || part > count) {
                        throw new IOException("Unexpected converted DEX part: " + name);
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Unexpected converted DEX part: " + name);
                }
            }
        }
        return result;
    }

    private static int readCount(File conf) throws IOException {
        if (!conf.isFile()) {
            return 1;
        }
        BufferedReader reader = new BufferedReader(new FileReader(conf));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0 || !DEX_COUNT_KEY.equals(line.substring(0, colon).trim())) {
                    continue;
                }
                String value = line.substring(colon + 1).trim();
                try {
                    int count = Integer.parseInt(value);
                    if (count < 1 || count > MAX_PARTS) {
                        throw new IOException("Invalid DEX part count: " + value);
                    }
                    return count;
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid DEX part count: " + value);
                }
            }
        } finally {
            reader.close();
        }
        return 1;
    }
}
