/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.io.File;

/** Validates profile names before they are used as children of templates/. */
public final class LegacyProfileName {
    public static final int MAX_LENGTH = 64;

    private LegacyProfileName() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Profile name is required");
        }
        String name = raw.trim();
        if (!isValid(name)) {
            throw new IllegalArgumentException("Invalid profile name");
        }
        return name;
    }

    public static boolean isValid(String name) {
        if (name == null || name.length() == 0 || name.length() > MAX_LENGTH
                || ".".equals(name) || "..".equals(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || c == '/' || c == '\\') {
                return false;
            }
        }
        return true;
    }

    public static boolean isSame(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    public static boolean isChildOf(File root, File candidate) {
        if (root == null || candidate == null) {
            return false;
        }
        try {
            String rootPath = root.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.startsWith(rootPath + File.separator)
                    && candidate.getName().equals(candidatePath.substring(rootPath.length() + 1));
        } catch (Exception ignored) {
            return false;
        }
    }
}
