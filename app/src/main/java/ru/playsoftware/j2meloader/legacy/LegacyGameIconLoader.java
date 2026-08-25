/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads installed MIDlet icons away from the launcher UI thread. */
public final class LegacyGameIconLoader {
    private static final int MAX_CACHED_ICONS = 24;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(
            MAX_CACHED_ICONS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > MAX_CACHED_ICONS;
        }
    };
    private final Map<String, PendingRequest> pending = new LinkedHashMap<String, PendingRequest>();
    private int generation;
    private boolean closed;

    public interface Callback {
        void onIconReady(Bitmap bitmap);
    }

    public void load(final LegacyAppCatalog.Game game, final int targetSize,
                     final Callback callback) {
        if (game == null || callback == null) {
            return;
        }
        final String key = game.getDirectory().getAbsolutePath();
        final Bitmap cached;
        final int requestGeneration;
        synchronized (this) {
            if (closed) {
                return;
            }
            cached = cache.get(key);
            if (cached != null) {
                callback.onIconReady(cached);
                return;
            }
            PendingRequest existing = pending.get(key);
            if (existing != null) {
                existing.callbacks.add(callback);
                return;
            }
            requestGeneration = generation;
            PendingRequest request = new PendingRequest(requestGeneration);
            request.callbacks.add(callback);
            pending.put(key, request);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = null;
                try {
                    bitmap = decode(game, targetSize);
                } catch (IOException ignored) {
                    // The adapter keeps the default launcher icon for unreadable archives.
                } catch (RuntimeException ignored) {
                    // BitmapFactory and old ZIP implementations can reject malformed images.
                } catch (OutOfMemoryError ignored) {
                    // A broken icon must never take down the low-memory launcher.
                }

                List<Callback> callbacks;
                synchronized (LegacyGameIconLoader.this) {
                    PendingRequest pendingRequest = pending.get(key);
                    if (pendingRequest != null
                            && pendingRequest.generation == requestGeneration) {
                        pending.remove(key);
                    }
                    if (bitmap == null || closed || requestGeneration != generation) {
                        return;
                    }
                    cache.put(key, bitmap);
                    callbacks = pendingRequest == null
                            ? new ArrayList<Callback>() : pendingRequest.callbacks;
                }
                for (Callback callback : callbacks) {
                    callback.onIconReady(bitmap);
                }
            }
        });
    }

    public synchronized void clear() {
        generation++;
        cache.clear();
        pending.clear();
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        generation++;
        cache.clear();
        pending.clear();
        executor.shutdownNow();
    }

    private static Bitmap decode(LegacyAppCatalog.Game game, int targetSize) throws IOException {
        File directory = game.getDirectory();
        File legacyIcon = new File(directory, "icon.png");
        if (legacyIcon.isFile()) {
            try {
                Bitmap bitmap = decodeFile(legacyIcon, targetSize);
                if (bitmap != null) {
                    return bitmap;
                }
            } catch (IOException ignored) {
                // Fall through to the original resource archive when available.
            }
        }
        String iconEntry = game.getIconEntry();
        if (iconEntry == null) {
            return null;
        }
        File resourceJar = new File(directory, "res.jar");
        if (!resourceJar.isFile()) {
            return null;
        }
        final ZipFile zip = new ZipFile(resourceJar);
        try {
            final ZipEntry entry = zip.getEntry(iconEntry);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            return decodeStream(new InputFactory() {
                @Override
                public InputStream open() throws IOException {
                    return zip.getInputStream(entry);
                }
            }, targetSize);
        } finally {
            zip.close();
        }
    }

    private static Bitmap decodeFile(File file, int targetSize) throws IOException {
        return decodeStream(new InputFactory() {
            @Override
            public InputStream open() throws IOException {
                return new FileInputStream(file);
            }
        }, targetSize);
    }

    private static Bitmap decodeStream(InputFactory factory, int targetSize) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream input = factory.open();
        try {
            BitmapFactory.decodeStream(input, null, bounds);
        } finally {
            input.close();
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetSize);
        input = factory.open();
        try {
            return BitmapFactory.decodeStream(input, null, options);
        } finally {
            input.close();
        }
    }

    private static int sampleSize(int width, int height, int targetSize) {
        int sample = 1;
        int safeTarget = Math.max(1, targetSize);
        while (width / sample > safeTarget || height / sample > safeTarget) {
            sample *= 2;
        }
        return sample;
    }

    private interface InputFactory {
        InputStream open() throws IOException;
    }

    private static final class PendingRequest {
        final int generation;
        final List<Callback> callbacks = new ArrayList<Callback>();

        PendingRequest(int generation) {
            this.generation = generation;
        }
    }
}
