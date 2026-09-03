/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import com.android.dx.command.dexer.Main;
import com.android.dx.command.dexer.DxContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Local-only installer for the API 10 build. Conversion may be run in a worker process and
 * reports progress through the platform-neutral listener.
 */
public final class LegacyInstaller {
    private static final String JAD_JAR_URL = "MIDlet-Jar-URL";
    private static final String NAME = "MIDlet-Name";
    private static final String VENDOR = "MIDlet-Vendor";
    private static final String VERSION = "MIDlet-Version";
    private static final String DEX = "converted.dex";
    private static final String CONF = "converted.dex.conf";
    private static final String RES = "res.jar";
    private static final String DEX_COUNT = "J2ME-Loader-Dex-Count";
    private static final int MAX_BATCH_CLASSES = 128;
    private static final long MAX_BATCH_BYTES = 512L * 1024L;

    private static final InstallProgressListener NO_OP = new InstallProgressListener() {
        @Override public void onStage(String stage) { }
        @Override public void onProgress(int completed, int total, String className) { }
        @Override public void onLog(String level, String message) { }
    };

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
        return install(source, NO_OP);
    }

    /** Install while reporting stages, class progress and converter diagnostics. */
    public InstallResult install(File source, InstallProgressListener progress) {
        if (progress == null) {
            progress = NO_OP;
        }
        File temp = null;
        File backup = null;
        try {
            progress.onStage("validating");
            progress.onLog("INFO", "Validating source archive");
            if (source == null || !source.isFile()) {
                return InstallResult.rejected("Source is not a file");
            }
            File jar = resolveJar(source);
            LegacyArchiveValidator.ArchiveInfo archiveInfo = LegacyArchiveValidator.inspect(jar);
            progress.onLog("INFO", "Archive contains " + archiveInfo.getClassCount()
                    + " class file(s)");
            progress.onLog("INFO", "Reading MIDlet manifest");
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
            progress.onStage("batching");
            int dexCount = convertBatches(jar, temp, archiveInfo.getClassCount(), progress);
            progress.onLog("INFO", "Copying game resources");
            copyFile(jar, new File(temp, RES));
            progress.onStage("publishing");
            writeDescriptor(new File(temp, CONF), descriptor, dexCount);

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
            progress.onLog("INFO", "Published " + descriptor.name + " (" + dexCount
                    + " DEX part(s))");
            return InstallResult.success(update ? InstallResult.Status.UPDATED
                            : InstallResult.Status.INSTALLED,
                    target, descriptor.name, descriptor.vendor, descriptor.version);
        } catch (SecurityException e) {
            progress.onLog("ERROR", exceptionText("security", e));
            return InstallResult.rejected(e.getMessage());
        } catch (IOException e) {
            progress.onLog("ERROR", exceptionText("io", e));
            return InstallResult.failed(e.getMessage());
        } catch (OutOfMemoryError e) {
            progress.onLog("ERROR", exceptionText("out-of-memory", e));
            return InstallResult.failed("Not enough memory to convert this game");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            progress.onLog("ERROR", exceptionText("runtime", e));
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

    private int convertBatches(File jar, File temp, int totalClasses,
            InstallProgressListener progress) throws IOException {
        if (totalClasses == 0) {
            // Keep the historical converter contract for synthetic/empty fixtures. A real dx
            // invocation will still reject an archive with no classes, while custom converters
            // used by embedders retain the old install(File) behaviour.
            File dex = new File(temp, DEX);
            converter.convert(jar, dex);
            assertDex035(dex);
            progress.onProgress(0, 0, "no classes");
            return 1;
        }
        ZipFile zip = new ZipFile(jar);
        int batchNumber = 0;
        int completedClasses = 0;
        int batchClasses = 0;
        long batchBytes = 0;
        File batchJar = null;
        JarOutputStream batchOutput = null;
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                long entryBytes = entry.getSize();
                if (entryBytes < 0) {
                    entryBytes = 0;
                }
                if (batchOutput == null || (batchClasses > 0
                        && (batchClasses >= MAX_BATCH_CLASSES
                        || batchBytes + entryBytes > MAX_BATCH_BYTES))) {
                    if (batchOutput != null) {
                        batchOutput.close();
                        batchOutput = null;
                        completedClasses = convertBatch(batchJar, temp, batchNumber,
                                completedClasses, totalClasses, progress);
                        deleteRecursively(batchJar);
                    }
                    batchNumber++;
                    batchClasses = 0;
                    batchBytes = 0;
                    batchJar = new File(temp, ".class-batch-" + batchNumber + ".jar");
                    batchOutput = new JarOutputStream(new FileOutputStream(batchJar));
                }
                ZipEntry outputEntry = new ZipEntry(entry.getName());
                outputEntry.setTime(entry.getTime());
                batchOutput.putNextEntry(outputEntry);
                InputStream input = zip.getInputStream(entry);
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        batchOutput.write(buffer, 0, read);
                    }
                } finally {
                    input.close();
                }
                batchOutput.closeEntry();
                batchClasses++;
                batchBytes += entryBytes;
            }
            if (batchOutput != null) {
                batchOutput.close();
                completedClasses = convertBatch(batchJar, temp, batchNumber,
                        completedClasses, totalClasses, progress);
                deleteRecursively(batchJar);
            }
            return batchNumber;
        } finally {
            if (batchOutput != null) {
                batchOutput.close();
            }
            if (batchJar != null && batchJar.exists()) {
                deleteRecursively(batchJar);
            }
            zip.close();
        }
    }

    private int convertBatch(File batchJar, File temp, int batchNumber,
            int completedClasses, int totalClasses, InstallProgressListener progress)
            throws IOException {
        File dex = new File(temp, batchNumber == 1 ? DEX : "converted." + batchNumber + ".dex");
        progress.onStage("converting");
        progress.onLog("INFO", "Converting class batch " + batchNumber);
        try {
            if (converter instanceof DxDexConverter) {
                ((DxDexConverter) converter).convert(batchJar, dex, progress,
                        completedClasses, totalClasses);
            } else {
                converter.convert(batchJar, dex);
            }
        } catch (IOException e) {
            progress.onLog("ERROR", "phase=converting batch=" + batchNumber + ": "
                    + exceptionText("io", e));
            throw e;
        } catch (OutOfMemoryError e) {
            progress.onLog("ERROR", "phase=converting batch=" + batchNumber + ": "
                    + exceptionText("out-of-memory", e));
            throw e;
        } catch (RuntimeException e) {
            progress.onLog("ERROR", "phase=converting batch=" + batchNumber + ": "
                    + exceptionText("runtime", e));
            throw e;
        }
        try {
            assertDex035(dex);
        } catch (IOException e) {
            progress.onLog("ERROR", "phase=verify-dex batch=" + batchNumber + ": "
                    + exceptionText("io", e));
            throw e;
        }
        int next = completedClasses + countClasses(batchJar);
        progress.onProgress(next, totalClasses, "batch " + batchNumber);
        return next;
    }

    private static int countClasses(File jar) throws IOException {
        int count = 0;
        ZipFile zip = new ZipFile(jar);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) count++;
            }
        } finally {
            zip.close();
        }
        return count;
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

    private static void writeDescriptor(File file, Descriptor descriptor, int dexCount) throws IOException {
        OutputStream output = new FileOutputStream(file);
        try {
            for (Map.Entry<String, String> entry : descriptor.attributes.entrySet()) {
                if (DEX_COUNT.equals(entry.getKey())) {
                    continue;
                }
                String text = entry.getKey() + ": " + entry.getValue() + "\n";
                output.write(text.getBytes("UTF-8"));
            }
            output.write((DEX_COUNT + ": " + dexCount + "\n").getBytes("UTF-8"));
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

    private static String exceptionText(String phase, Throwable error) {
        String message = error.getMessage();
        return phase + ": " + error.getClass().getName()
                + (message == null ? "" : ": " + message);
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
            convert(jar, dex, NO_OP, 0, 0);
        }

        void convert(File jar, File dex, final InstallProgressListener progress,
                final int completed, final int total) throws IOException {
            String[] args = dexArguments(jar, dex);
            OutputStream out = new LineOutputStream(progress, "INFO");
            OutputStream err = new LineOutputStream(progress, "ERROR");
            final int[] processed = new int[]{completed};
            DxContext context = new DxContext(out, err, new DxContext.ProgressListener() {
                @Override
                public void onClassProcessed(String className) {
                    progress.onProgress(++processed[0], total, className);
                }
            });
            try {
                Main.run(args, context);
            } finally {
                try {
                    out.close();
                } finally {
                    err.close();
                }
            }
            if (!dex.isFile()) {
                throw new IOException("dx did not produce a DEX file");
            }
        }
    }

    private static final class LineOutputStream extends OutputStream {
        private final InstallProgressListener listener;
        private final String level;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        LineOutputStream(InstallProgressListener listener, String level) {
            this.listener = listener;
            this.level = level;
        }

        @Override
        public void write(int value) throws IOException {
            if (value == '\n') {
                flushLine();
            } else if (value != '\r') {
                line.write(value);
            }
        }

        @Override
        public void flush() throws IOException {
            flushLine();
        }

        @Override
        public void close() throws IOException {
            flushLine();
        }

        private void flushLine() {
            if (line.size() == 0) return;
            listener.onLog(level, new String(line.toByteArray()));
            line.reset();
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
