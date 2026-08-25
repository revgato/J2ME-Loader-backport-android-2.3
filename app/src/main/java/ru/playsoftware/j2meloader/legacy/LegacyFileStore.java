/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/** Small transactional file writer for the API 10 external-storage layout. */
public final class LegacyFileStore {
    private static final int BUFFER_SIZE = 8192;

    private LegacyFileStore() {
    }

    public static void writeUtf8(File target, String value) throws IOException {
        File temp = temporarySibling(target);
        FileOutputStream output = null;
        Writer writer = null;
        try {
            output = new FileOutputStream(temp);
            writer = new OutputStreamWriter(output, "UTF-8");
            writer.write(value);
            writer.flush();
            output.getFD().sync();
        } finally {
            if (writer != null) {
                writer.close();
            } else if (output != null) {
                output.close();
            }
        }
        publish(temp, target);
    }

    public static void copy(File source, File target) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Source file is missing: " + source);
        }
        File temp = temporarySibling(target);
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(temp);
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count != 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.flush();
            ((FileOutputStream) output).getFD().sync();
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        publish(temp, target);
    }

    private static File temporarySibling(File target) throws IOException {
        if (target == null || target.getName().length() == 0) {
            throw new IOException("Target file is missing");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        File temp = new File(parent, target.getName() + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Cannot remove stale temporary file: " + temp);
        }
        return temp;
    }

    private static void publish(File temp, File target) throws IOException {
        File backup = new File(target.getPath() + ".bak");
        boolean hadTarget = target.exists();
        if (backup.exists() && !backup.delete()) {
            temp.delete();
            throw new IOException("Cannot remove stale backup file: " + backup);
        }
        if (hadTarget && !target.renameTo(backup)) {
            temp.delete();
            throw new IOException("Cannot stage existing file: " + target);
        }
        if (!temp.renameTo(target)) {
            if (hadTarget) {
                backup.renameTo(target);
            }
            temp.delete();
            throw new IOException("Cannot publish file: " + target);
        }
        if (hadTarget && !backup.delete()) {
            // The new file is valid. Leaving a backup is safer than deleting either copy.
            throw new IOException("Published file but could not remove backup: " + backup);
        }
    }
}
