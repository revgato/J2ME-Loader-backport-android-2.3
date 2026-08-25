/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** A host-testable one-to-one mapping between physical and MIDP key codes. */
public final class LegacyKeyMapping {
    private final TreeMap<Integer, Integer> values = new TreeMap<Integer, Integer>();

    public static LegacyKeyMapping fromArrays(int[] physicalKeys, int[] canvasKeys) {
        if (physicalKeys == null || canvasKeys == null
                || physicalKeys.length != canvasKeys.length) {
            throw new IllegalArgumentException("Key mapping arrays must have the same length");
        }
        LegacyKeyMapping result = new LegacyKeyMapping();
        for (int i = 0; i < physicalKeys.length; i++) {
            result.values.put(physicalKeys[i], canvasKeys[i]);
        }
        return result;
    }

    public LegacyKeyMapping copy() {
        LegacyKeyMapping result = new LegacyKeyMapping();
        result.values.putAll(values);
        return result;
    }

    /** Assigns one physical key to one MIDP key and removes both old duplicates. */
    public void assign(int physicalKey, int canvasKey) {
        Iterator<Map.Entry<Integer, Integer>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (entry.getKey() == physicalKey || entry.getValue() == canvasKey) {
                iterator.remove();
            }
        }
        values.put(physicalKey, canvasKey);
    }

    public void reset(LegacyKeyMapping defaults) {
        if (defaults == null) {
            throw new IllegalArgumentException("Default key mapping is required");
        }
        values.clear();
        values.putAll(defaults.values);
    }

    public boolean hasCanvasKey(int canvasKey) {
        return physicalKeyFor(canvasKey, Integer.MIN_VALUE) != Integer.MIN_VALUE;
    }

    public int physicalKeyFor(int canvasKey, int fallback) {
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            if (entry.getValue() == canvasKey) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    public int[] physicalKeys() {
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer key : values.keySet()) {
            result[index++] = key;
        }
        return result;
    }

    public int[] canvasKeys() {
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values.values()) {
            result[index++] = value;
        }
        return result;
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LegacyKeyMapping)) {
            return false;
        }
        return values.equals(((LegacyKeyMapping) object).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
