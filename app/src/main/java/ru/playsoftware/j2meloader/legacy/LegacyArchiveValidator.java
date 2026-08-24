/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates an untrusted JAR before it is handed to the converter or extracted.
 * The limits deliberately match the IS14SH backport security budget.
 */
public final class LegacyArchiveValidator {
    public static final long MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_ENTRIES = 4096;
    public static final long MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L;

    private LegacyArchiveValidator() {
    }

    public static void validate(File archive) throws IOException {
        if (archive == null || !archive.isFile() || !archive.canRead()) {
            throw new IOException("Archive is not a readable file");
        }
        if (archive.length() > MAX_ARCHIVE_BYTES) {
            throw new IOException("Archive exceeds 32 MiB limit");
        }

        long extractedBytes = 0;
        int entries = 0;
        ZipFile zip = new ZipFile(archive);
        try {
            Enumeration<? extends ZipEntry> all = zip.entries();
            byte[] buffer = new byte[8192];
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("Archive contains more than 4096 entries");
                }
                validateEntryName(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }

                long declared = entry.getSize();
                if (declared > MAX_UNCOMPRESSED_BYTES
                        || (declared >= 0 && extractedBytes > MAX_UNCOMPRESSED_BYTES - declared)) {
                    throw new IOException("Archive exceeds 128 MiB extracted limit");
                }

                // Do not trust the central directory size. Reading also catches ZIP bombs
                // with an unknown or deliberately incorrect uncompressed-size field.
                InputStream input = zip.getInputStream(entry);
                try {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > 0) {
                            if (extractedBytes > MAX_UNCOMPRESSED_BYTES - read) {
                                throw new IOException("Archive exceeds 128 MiB extracted limit");
                            }
                            extractedBytes += read;
                        }
                    }
                } finally {
                    input.close();
                }
            }
        } finally {
            zip.close();
        }
    }

    /** Reject absolute names, drive-letter paths and parent traversal on either slash style. */
    static void validateEntryName(String rawName) throws IOException {
        if (rawName == null || rawName.length() == 0) {
            throw new IOException("Archive contains an empty entry name");
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.indexOf(':') >= 0) {
            throw new IOException("Archive contains an absolute entry: " + rawName);
        }
        String[] parts = name.split("/");
        for (String part : parts) {
            if ("..".equals(part) || ".".equals(part) || part.length() == 0) {
                throw new IOException("Archive contains a non-canonical entry: " + rawName);
            }
        }
        // FileInputStream is intentionally used here so this class remains API 10 friendly:
        // no java.nio Path/Files APIs are loaded by Dalvik.
        if (new File(name).isAbsolute()) {
            throw new IOException("Archive contains an absolute entry: " + rawName);
        }
    }
}
