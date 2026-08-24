/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import com.android.dx.command.dexer.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Synchronous local-only installer for the API 10 build. UI code should call this class from
 * an executor and only publish the returned {@link InstallResult} on the UI thread.
 */
public final class LegacyInstaller {
    private static final String JAD_JAR_URL = "MIDlet-Jar-URL";
    private static final String NAME = "MIDlet-Name";
    private static final String VENDOR = "MIDlet-Vendor";
    private static final String VERSION = "MIDlet-Version";
    private static final String DEX = "converted.dex";
    private static final String CONF = "converted.dex.conf";
    private static final String RES = "res.jar";

    private final File emulatorDirectory;
    private final DexConverter converter;

    public LegacyInstaller(File emulatorDirectory) {
        this(emulatorDirectory, new DxDexConverter());
    }

    public LegacyInstaller(File emulatorDirectory, DexConverter converter) {
        if (emulatorDirectory == null || converter == null) {
            throw new NullPointerException();
        }
        this.emulatorDirectory = emulatorDirectory;
        this.converter = converter;
    }

    /** Install a local .jar or .jad and return a structured result instead of throwing UI errors. */
    public InstallResult install(File source) {
        File temp = null;
        File backup = null;
        try {
            if (source == null || !source.isFile()) {
                return InstallResult.rejected("Source is not a file");
            }
            File jar = resolveJar(source);
            LegacyArchiveValidator.validate(jar);
            Descriptor descriptor = readDescriptor(jar);
            if (descriptor.name.length() == 0) {
                return InstallResult.rejected("JAR has no MIDlet-Name");
            }

            File converted = new File(emulatorDirectory, "converted");
            ensureDirectory(converted);
            File target = findExisting(converted, descriptor);
            boolean update = target != null;
            if (!update) {
                target = new File(converted, uniqueDirectoryName(converted, descriptor.name));
            }

            temp = new File(converted, ".tmp-" + Long.toHexString(System.currentTimeMillis())
                    + "-" + Integer.toHexString(System.identityHashCode(source)));
            ensureDirectory(temp);
            File dex = new File(temp, DEX);
            converter.convert(jar, dex);
            assertDex035(dex);
            copyFile(jar, new File(temp, RES));
            writeDescriptor(new File(temp, CONF), descriptor);

            if (target.exists()) {
                backup = new File(converted, ".backup-" + target.getName());
                deleteRecursively(backup);
                if (!target.renameTo(backup)) {
                    throw new IOException("Could not move existing game to backup");
                }
            }
            if (!temp.renameTo(target)) {
                if (backup != null && backup.exists()) {
                    backup.renameTo(target);
                }
                throw new IOException("Could not atomically publish converted game");
            }
            // RMS and config live outside converted and are deliberately untouched.
            if (backup != null) {
                deleteRecursively(backup);
            }
            temp = null;
            return InstallResult.success(update ? InstallResult.Status.UPDATED
                            : InstallResult.Status.INSTALLED,
                    target, descriptor.name, descriptor.vendor, descriptor.version);
        } catch (SecurityException e) {
            return InstallResult.rejected(e.getMessage());
        } catch (IOException e) {
            return InstallResult.failed(e.getMessage());
        } catch (OutOfMemoryError e) {
            return InstallResult.failed("Not enough memory to convert this game");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            return InstallResult.failed(message == null ? "Game conversion failed" : message);
        } finally {
            if (temp != null) {
                deleteRecursively(temp);
            }
            if (backup != null && backup.exists()) {
                // A failed publish must never leave the old version hidden.
                File target = new File(backup.getParentFile(), backup.getName().replace(".backup-", ""));
                if (!target.exists()) {
                    backup.renameTo(target);
                }
            }
        }
    }

    private File resolveJar(File source) throws IOException {
        String lower = source.getName().toLowerCase();
        if (lower.endsWith(".jar")) {
            return source.getCanonicalFile();
        }
        if (!lower.endsWith(".jad")) {
            throw new IOException("Only local JAR and JAD files are supported");
        }
        Map<String, String> jad = readProperties(source);
        String jarUrl = jad.get(JAD_JAR_URL);
        if (jarUrl == null || jarUrl.length() == 0) {
            throw new IOException("JAD has no MIDlet-Jar-URL");
        }
        int colon = jarUrl.indexOf(':');
        int slash = jarUrl.indexOf('/');
        boolean hasScheme = colon >= 0 && (slash < 0 || colon < slash);
        if (hasScheme || jarUrl.indexOf("://") >= 0 || jarUrl.startsWith("/")
                || jarUrl.indexOf('\\') >= 0) {
            throw new IOException("JAD must reference a local JAR in the same directory");
        }
        String[] pieces = jarUrl.split("/");
        for (String piece : pieces) {
            if ("..".equals(piece) || ".".equals(piece)) {
                throw new IOException("JAD path traversal is not allowed");
            }
        }
        File parent = source.getParentFile();
        File jar = new File(parent, jarUrl).getCanonicalFile();
        if (jar.getParentFile() == null || !jar.getParentFile().equals(parent.getCanonicalFile())) {
            throw new IOException("JAD JAR must be beside the JAD");
        }
        return jar;
    }

    private static Descriptor readDescriptor(File jar) throws IOException {
        JarFile file = new JarFile(jar, false);
        try {
            Manifest manifest = file.getManifest();
            if (manifest == null) {
                throw new IOException("JAR has no META-INF/MANIFEST.MF");
            }
            LinkedHashMap<String, String> attributes = new LinkedHashMap<String, String>();
            for (Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet()) {
                attributes.put(String.valueOf(entry.getKey()), value(String.valueOf(entry.getValue())));
            }
            return new Descriptor(attributes);
        } finally {
            file.close();
        }
    }

    private File findExisting(File converted, Descriptor descriptor) throws IOException {
        File[] children = converted.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (!child.isDirectory() || child.getName().startsWith(".")) {
                continue;
            }
            Map<String, String> old = readProperties(new File(child, CONF));
            if (descriptor.name.equals(old.get(NAME)) && descriptor.vendor.equals(value(old.get(VENDOR)))) {
                return child;
            }
        }
        return null;
    }

    private static String uniqueDirectoryName(File converted, String name) {
        String clean = name.replaceAll("[^A-Za-z0-9._-]", "_").trim();
        if (clean.length() == 0) {
            clean = "midlet";
        }
        File candidate = new File(converted, clean);
        int suffix = 1;
        while (candidate.exists()) {
            candidate = new File(converted, clean + "_" + suffix++);
        }
        return candidate.getName();
    }

    private static void writeDescriptor(File file, Descriptor descriptor) throws IOException {
        OutputStream output = new FileOutputStream(file);
        try {
            for (Map.Entry<String, String> entry : descriptor.attributes.entrySet()) {
                String text = entry.getKey() + ": " + entry.getValue() + "\n";
                output.write(text.getBytes("UTF-8"));
            }
        } finally {
            output.close();
        }
    }

    private static Map<String, String> readProperties(File file) throws IOException {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        if (!file.isFile()) {
            return result;
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    result.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
        } finally {
            reader.close();
        }
        return result;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create directory: " + directory);
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        InputStream input = new FileInputStream(source);
        OutputStream output = new FileOutputStream(destination);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
            output.close();
        }
    }

    private static void assertDex035(File dex) throws IOException {
        InputStream input = new FileInputStream(dex);
        try {
            byte[] magic = new byte[4];
            int read = input.read(magic);
            if (read != 4 || magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x' || magic[3] != '\n') {
                throw new IOException("Converter did not produce a DEX file");
            }
            int version = input.read();
            if (version != '0' || input.read() != '3' || input.read() != '5') {
                throw new IOException("Converter produced a DEX newer than 035");
            }
        } finally {
            input.close();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public interface DexConverter {
        void convert(File jar, File dex) throws IOException;
    }

    private static final class DxDexConverter implements DexConverter {
        @Override
        public void convert(File jar, File dex) throws IOException {
            String[] args = dexArguments(jar, dex);
            // Main.main parses the min-sdk flag and throws when dx cannot produce an output.
            Main.main(args);
            if (!dex.isFile()) {
                throw new IOException("dx did not produce a DEX file");
            }
        }
    }

    static String[] dexArguments(File jar, File dex) {
        return new String[]{"--no-optimize", "--no-locals", "--positions=none", "--num-threads=1",
                "--core-library", "--min-sdk-version=10", "--output=" + dex.getAbsolutePath(),
                jar.getAbsolutePath()};
    }

    private static final class Descriptor {
        final Map<String, String> attributes;
        final String name;
        final String vendor;
        final String version;

        Descriptor(Map<String, String> attributes) {
            this.attributes = new LinkedHashMap<String, String>(attributes);
            this.name = value(this.attributes.get(NAME));
            this.vendor = value(this.attributes.get(VENDOR));
            this.version = value(this.attributes.get(VERSION));
        }
    }
}
