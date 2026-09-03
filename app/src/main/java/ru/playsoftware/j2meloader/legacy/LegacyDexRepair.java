/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import com.android.dex.ClassDef;
import com.android.dex.Dex;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.Main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Repairs converter output when a JAR entry name does not match its class declaration. */
public final class LegacyDexRepair {
    private static final String COMPAT_DEX = "converted.compat.dex";
    private static final String MARKER = "converted.compat.marker";
    private static final String MARKER_VERSION = "2";
    private static final String STATE_NONE = "NONE";
    private static final String STATE_READY = "READY";
    private static final byte[] DEX035 = new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0};
    private static final int MAX_CLASS_BYTES = 8 * 1024 * 1024;
    private static final String[] PROTECTED_PREFIXES = new String[]{
            "java/", "javax/", "android/", "dalvik/", "org/microemu/",
            "ru/playsoftware/j2meloader/", "com/android/"
    };

    private LegacyDexRepair() {
    }

    public enum Status {
        NONE, READY
    }

    public static final class Result {
        private final Status status;
        private final File compatDex;
        private final int classCount;

        private Result(Status status, File compatDex, int classCount) {
            this.status = status;
            this.compatDex = compatDex;
            this.classCount = classCount;
        }

        public Status getStatus() {
            return status;
        }

        public File getCompatDex() {
            return compatDex;
        }

        public int getClassCount() {
            return classCount;
        }
    }

    /** Prepare or reuse the derived DEX beside the converted DEX parts. */
    public static Result prepare(File appDir, File scratchDir) throws IOException {
        if (appDir == null || scratchDir == null || !appDir.isDirectory()) {
            throw new IOException("MIDlet application directory is missing");
        }
        File resJar = new File(appDir, "res.jar");
        java.util.List<File> dexParts = LegacyDexFiles.list(appDir);
        if (!resJar.isFile()) {
            throw new IOException("MIDlet converted artifacts are incomplete");
        }
        String primaryFingerprint = fingerprintParts(dexParts);
        String resFingerprint = fingerprint(resJar);
        File compatDex = new File(appDir, COMPAT_DEX);
        File marker = new File(appDir, MARKER);

        Marker cached = readMarker(marker);
        if (cached != null && primaryFingerprint.equals(cached.primary)
                && resFingerprint.equals(cached.res)) {
            if (STATE_NONE.equals(cached.state)) {
                return new Result(Status.NONE, null, 0);
            }
            if (STATE_READY.equals(cached.state) && isDex035(compatDex)
                    && cached.compat.equals(fingerprint(compatDex))) {
                try {
                    verifyClassCount(compatDex, cached.classCount);
                    return new Result(Status.READY, compatDex, cached.classCount);
                } catch (IOException ignored) {
                    // Rebuild a damaged derived artifact from the immutable source files.
                }
            }
        }

        // Validate the complete archive before reading individual entries. This bounds both
        // path handling and decompression work for files copied directly to external storage.
        LegacyArchiveValidator.validate(resJar);
        Set<String> primaryClasses = new HashSet<String>();
        for (File dexPart : dexParts) {
            primaryClasses.addAll(readDexClasses(dexPart));
        }
        Map<String, byte[]> repairClasses = findRepairClasses(resJar, primaryClasses);
        if (repairClasses.isEmpty()) {
            LegacyFileStore.writeUtf8(marker, markerText(STATE_NONE, 0, primaryFingerprint,
                    resFingerprint, ""));
            return new Result(Status.NONE, null, 0);
        }

        ensureDirectory(scratchDir);
        File work = new File(scratchDir, "legacy-dex-repair-"
                + Long.toHexString(System.currentTimeMillis()) + "-"
                + Integer.toHexString(System.identityHashCode(resJar)));
        ensureDirectory(work);
        File normalizedJar = new File(work, "normalized.jar");
        File generatedDex = new File(work, "compat.dex");
        try {
            writeNormalizedJar(normalizedJar, repairClasses);
            Main.main(LegacyInstaller.dexArguments(normalizedJar, generatedDex));
            if (!isDex035(generatedDex)) {
                throw new IOException("dx did not produce a DEX 035 compatibility file");
            }
            verifyClasses(generatedDex, repairClasses.keySet());
            LegacyFileStore.copy(generatedDex, compatDex);
            LegacyFileStore.writeUtf8(marker, markerText(STATE_READY, repairClasses.size(),
                    primaryFingerprint, resFingerprint, fingerprint(compatDex)));
            return new Result(Status.READY, compatDex, repairClasses.size());
        } finally {
            deleteRecursively(work);
        }
    }

    static boolean isProtectedNamespace(String internalName) {
        if (internalName == null || internalName.length() == 0) return true;
        for (String prefix : PROTECTED_PREFIXES) {
            if (internalName.startsWith(prefix)) return true;
        }
        return false;
    }

    private static Map<String, byte[]> findRepairClasses(File resJar, Set<String> primaryClasses)
            throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        ZipFile zip = new ZipFile(resJar);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                String entryName = entry.getName();
                if (entryName.indexOf('\\') >= 0) {
                    throw new IOException("Class entry uses a non-canonical path: " + entryName);
                }
                String declaredPath = entryName.substring(0, entryName.length() - 6);
                if (declaredPath.length() == 0) {
                    throw new IOException("Class entry has an empty name");
                }
                if (isProtectedNamespace(declaredPath)) {
                    throw new IOException("Protected class namespace: " + declaredPath);
                }
                byte[] bytes = readClass(zip, entry);
                DirectClassFile classFile = new DirectClassFile(bytes, entryName, false);
                classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
                classFile.getMagic();
                String actualName = classFile.getThisClass().getClassType().getClassName();
                validateInternalName(actualName);
                if (isProtectedNamespace(actualName)) {
                    throw new IOException("Protected class namespace: " + actualName);
                }
                if (declaredPath.equals(actualName)) continue;
                String descriptor = "L" + actualName + ";";
                if (primaryClasses.contains(descriptor)) continue;
                if (result.containsKey(actualName)) {
                    throw new IOException("Duplicate repaired class: " + actualName);
                }
                result.put(actualName, bytes);
            }
        } finally {
            zip.close();
        }
        return result;
    }

    private static byte[] readClass(ZipFile zip, ZipEntry entry) throws IOException {
        long declared = entry.getSize();
        if (declared > MAX_CLASS_BYTES) {
            throw new IOException("Class entry is too large: " + entry.getName());
        }
        InputStream input = zip.getInputStream(entry);
        try {
            if (declared >= 0 && declared <= MAX_CLASS_BYTES) {
                byte[] result = new byte[(int) declared];
                int offset = 0;
                while (offset < result.length) {
                    int count = input.read(result, offset, result.length - offset);
                    if (count < 0) throw new IOException("Truncated class entry: " + entry.getName());
                    if (count > 0) offset += count;
                }
                if (input.read() != -1) {
                    throw new IOException("Class entry is larger than declared: " + entry.getName());
                }
                return result;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_CLASS_BYTES - output.size()) {
                    throw new IOException("Class entry is too large: " + entry.getName());
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void writeNormalizedJar(File jar, Map<String, byte[]> classes) throws IOException {
        ZipOutputStream output = new ZipOutputStream(new FileOutputStream(jar));
        try {
            for (Map.Entry<String, byte[]> classEntry : classes.entrySet()) {
                ZipEntry entry = new ZipEntry(classEntry.getKey() + ".class");
                output.putNextEntry(entry);
                output.write(classEntry.getValue());
                output.closeEntry();
            }
        } finally {
            output.close();
        }
    }

    private static Set<String> readDexClasses(File dexFile) throws IOException {
        Set<String> result = new HashSet<String>();
        Dex dex = new Dex(dexFile);
        for (ClassDef classDef : dex.classDefs()) {
            result.add(dex.typeNames().get(classDef.getTypeIndex()));
        }
        return result;
    }

    private static String fingerprintParts(java.util.List<File> dexParts) throws IOException {
        StringBuilder result = new StringBuilder();
        for (File dexPart : dexParts) {
            result.append(dexPart.getName()).append('=').append(fingerprint(dexPart)).append('|');
        }
        return result.toString();
    }

    private static void verifyClasses(File dexFile, Set<String> expected) throws IOException {
        Set<String> actual = readDexClasses(dexFile);
        for (String name : expected) {
            if (!actual.contains("L" + name + ";")) {
                throw new IOException("Compatibility DEX is missing " + name);
            }
        }
    }

    private static void verifyClassCount(File dexFile, int expected) throws IOException {
        if (readDexClasses(dexFile).size() < expected) {
            throw new IOException("Compatibility DEX is incomplete");
        }
    }

    private static void validateInternalName(String name) throws IOException {
        if (name == null || name.length() == 0 || name.startsWith("/") || name.endsWith("/")) {
            throw new IOException("Class declares an invalid name");
        }
        String[] parts = name.split("/");
        for (String part : parts) {
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)
                    || part.indexOf('\\') >= 0 || part.indexOf('.') >= 0) {
                throw new IOException("Class declares an invalid name: " + name);
            }
        }
    }

    private static String fingerprint(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = new FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            } finally {
                input.close();
            }
            byte[] bytes = digest.digest();
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static String markerText(String state, int classCount, String primary, String res,
                                     String compat) {
        return "version=" + MARKER_VERSION + "\nstate=" + state + "\nclasses=" + classCount
                + "\nprimary=" + primary + "\nres=" + res + "\ncompat=" + compat + "\n";
    }

    private static Marker readMarker(File marker) {
        if (!marker.isFile() || marker.length() > 4096L) return null;
        try {
            String text = new String(readBytes(marker), "UTF-8");
            Map<String, String> values = new HashMap<String, String>();
            String[] lines = text.split("\\n");
            for (String line : lines) {
                int separator = line.indexOf('=');
                if (separator > 0) values.put(line.substring(0, separator),
                        line.substring(separator + 1));
            }
            if (!MARKER_VERSION.equals(values.get("version"))) return null;
            String state = values.get("state");
            String primary = values.get("primary");
            String res = values.get("res");
            String compat = values.get("compat");
            int count = Integer.parseInt(values.get("classes"));
            if ((STATE_NONE.equals(state) && count != 0) || (!STATE_NONE.equals(state)
                    && !STATE_READY.equals(state))) return null;
            if (primary == null || res == null || compat == null) return null;
            return new Marker(state, primary, res, compat, count);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        InputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 8192));
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static boolean isDex035(File file) throws IOException {
        if (!file.isFile() || file.length() < DEX035.length) return false;
        InputStream input = new FileInputStream(file);
        try {
            byte[] magic = new byte[DEX035.length];
            int offset = 0;
            while (offset < magic.length) {
                int count = input.read(magic, offset, magic.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            for (int i = 0; i < magic.length; i++) if (magic[i] != DEX035[i]) return false;
            return true;
        } finally {
            input.close();
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create repair scratch directory: " + directory);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static final class Marker {
        final String state;
        final String primary;
        final String res;
        final String compat;
        final int classCount;

        Marker(String state, String primary, String res, String compat, int classCount) {
            this.state = state;
            this.primary = primary;
            this.res = res;
            this.compat = compat;
            this.classCount = classCount;
        }
    }
}
