/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package javax.microedition.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a Dalvik-compatible JAR wrapper for a raw DEX stored on external storage. */
final class DexJarCache {
    private static final byte[] DEX035 = new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0};
    private static final String ENTRY_NAME = "classes.dex";
    private static final String CACHE_NAME = "midlet-code.jar";

    private DexJarCache() {
    }

    static File create(File rawDex, File cacheDir) throws IOException {
        if (rawDex == null || cacheDir == null || !rawDex.isFile()) {
            throw new IOException("MIDlet DEX is missing");
        }

        FileInputStream input = new FileInputStream(rawDex);
        try {
            byte[] magic = new byte[DEX035.length];
            int offset = 0;
            while (offset < magic.length) {
                int count = input.read(magic, offset, magic.length - offset);
                if (count < 0) {
                    throw new IOException("MIDlet DEX is truncated");
                }
                offset += count;
            }
            for (int i = 0; i < DEX035.length; i++) {
                if (magic[i] != DEX035[i]) {
                    throw new IOException("MIDlet DEX is not version 035");
                }
            }

            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                throw new IOException("Can't create DEX cache directory: " + cacheDir);
            }

            File temporary = File.createTempFile("midlet-code-", ".tmp", cacheDir);
            boolean published = false;
            try {
                ZipOutputStream output = new ZipOutputStream(new FileOutputStream(temporary));
                try {
                    output.putNextEntry(new ZipEntry(ENTRY_NAME));
                    output.write(magic);
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.closeEntry();
                } finally {
                    output.close();
                }

                File result = new File(cacheDir, CACHE_NAME);
                if (result.exists() && !result.delete()) {
                    throw new IOException("Can't replace DEX cache: " + result);
                }
                if (!temporary.renameTo(result)) {
                    throw new IOException("Can't publish DEX cache: " + result);
                }
                published = true;
                return result;
            } finally {
                if (!published && temporary.exists()) {
                    temporary.delete();
                }
            }
        } finally {
            input.close();
        }
    }
}
