/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Default physical-key mapping for the Sharp AQUOS IS14SH slide keypad. */
public final class Is14shKeyProfile {
    // Android KeyEvent values are kept as numbers so this profile can be unit-tested on a host
    // and does not make the legacy Dalvik verifier resolve newer KeyEvent constants.
    public static final int KEYCODE_CALL = 5;
    public static final int KEYCODE_ENDCALL = 6;
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_1 = 8;
    public static final int KEYCODE_2 = 9;
    public static final int KEYCODE_3 = 10;
    public static final int KEYCODE_4 = 11;
    public static final int KEYCODE_5 = 12;
    public static final int KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14;
    public static final int KEYCODE_8 = 15;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_STAR = 17;
    public static final int KEYCODE_POUND = 18;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_EXPLORER = 64;
    public static final int KEYCODE_ENVELOPE = 65;

    private final Map<Integer, String> keyNames;

    private Is14shKeyProfile(Map<Integer, String> keyNames) {
        this.keyNames = Collections.unmodifiableMap(keyNames);
    }

    public static Is14shKeyProfile forDevice(String model, String device) {
        if (!matchesDevice(model, device)) {
            return null;
        }
        LinkedHashMap<Integer, String> keys = new LinkedHashMap<Integer, String>();
        keys.put(KEYCODE_0, "0");
        keys.put(KEYCODE_1, "1");
        keys.put(KEYCODE_2, "2");
        keys.put(KEYCODE_3, "3");
        keys.put(KEYCODE_4, "4");
        keys.put(KEYCODE_5, "5");
        keys.put(KEYCODE_6, "6");
        keys.put(KEYCODE_7, "7");
        keys.put(KEYCODE_8, "8");
        keys.put(KEYCODE_9, "9");
        keys.put(KEYCODE_STAR, "*");
        keys.put(KEYCODE_POUND, "#");
        keys.put(KEYCODE_DPAD_UP, "UP");
        keys.put(KEYCODE_DPAD_DOWN, "DOWN");
        keys.put(KEYCODE_DPAD_LEFT, "LEFT");
        keys.put(KEYCODE_DPAD_RIGHT, "RIGHT");
        keys.put(KEYCODE_DPAD_CENTER, "FIRE");
        keys.put(KEYCODE_ENTER, "ENTER");
        keys.put(KEYCODE_BACK, "BACK");
        keys.put(KEYCODE_CALL, "CALL");
        keys.put(KEYCODE_ENDCALL, "END");
        keys.put(KEYCODE_ENVELOPE, "MAIL");
        keys.put(KEYCODE_EXPLORER, "BROWSER");
        return new Is14shKeyProfile(keys);
    }

    public static boolean matchesDevice(String model, String device) {
        String m = model == null ? "" : model.toLowerCase(Locale.US);
        String d = device == null ? "" : device.toLowerCase(Locale.US);
        return m.indexOf("is14sh") >= 0 || d.indexOf("is14sh") >= 0
                || m.indexOf("sharp") >= 0 && d.indexOf("is14") >= 0;
    }

    public Map<Integer, String> getKeyNames() {
        return keyNames;
    }
}
