/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

/** Capability allow-list exposed to MIDlets on the Android 2.3 build. */
public final class LegacyCapabilities {
    private static final String[] CONTENT_TYPES = new String[]{
            "audio/midi", "audio/x-tone-seq", "audio/wav", "audio/x-wav", "audio/mpeg"
    };

    private LegacyCapabilities() {
    }

    public static String[] getSupportedContentTypes() {
        return CONTENT_TYPES.clone();
    }

    public static boolean supportsContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        for (String supported : CONTENT_TYPES) {
            if (supported.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }

    public static UnsupportedOperationException unsupported(String capability) {
        return new UnsupportedOperationException("Unsupported on Android 2.3 legacy build: " + capability);
    }
}
