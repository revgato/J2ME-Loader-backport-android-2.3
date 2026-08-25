/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

/** Bounds shared by the API 10 configuration editor and its host-side tests. */
public final class LegacyConfigValidation {
    public static final int MIN_SCREEN = 64;
    public static final int MAX_SCREEN = 1024;
    public static final int MIN_SCALE = 25;
    public static final int MAX_SCALE = 400;
    public static final int MAX_FPS = 120;
    public static final int MAX_FONT = 96;
    public static final int MAX_SYSTEM_PROPERTIES = 64 * 1024;

    private LegacyConfigValidation() {
    }

    public static void validateScreen(int width, int height) {
        if (width < MIN_SCREEN || width > MAX_SCREEN
                || height < MIN_SCREEN || height > MAX_SCREEN) {
            throw new IllegalArgumentException("Screen size must be 64..1024");
        }
    }

    public static void validateScale(int scale) {
        if (scale < MIN_SCALE || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Scale must be 25..400");
        }
    }

    public static void validateFps(int fps) {
        if (fps < 0 || fps > MAX_FPS) {
            throw new IllegalArgumentException("FPS must be 0..120");
        }
    }

    public static void validateFont(int size) {
        if (size < 0 || size > MAX_FONT) {
            throw new IllegalArgumentException("Font size must be 0..96");
        }
    }

    public static void validateSystemProperties(String value) {
        if (value == null || value.length() > MAX_SYSTEM_PROPERTIES) {
            throw new IllegalArgumentException("System properties are too large");
        }
        String[] lines = value.split("\\r?\\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() != 0 && trimmed.indexOf(':') <= 0) {
                throw new IllegalArgumentException("Every property must use key: value");
            }
        }
    }
}
