/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Build;

/** Small API 10-safe probe used before enabling GLES2 and physical-key defaults. */
public final class LegacyDeviceProbe {
    private LegacyDeviceProbe() {
    }

    public static Result read(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) {
            manager.getMemoryInfo(memory);
        }
        ConfigurationInfo configuration = manager == null ? null : manager.getDeviceConfigurationInfo();
        int glEs = configuration == null ? 0 : configuration.reqGlEsVersion;
        return new Result(Build.VERSION.SDK_INT, Build.MODEL, Build.DEVICE, Build.CPU_ABI,
                memory.availMem, memory.threshold, glEs);
    }

    public static final class Result {
        public final int api;
        public final String model;
        public final String device;
        public final String abi;
        public final long availableMemory;
        public final long memoryThreshold;
        public final int glEsVersion;

        Result(int api, String model, String device, String abi, long availableMemory,
               long memoryThreshold, int glEsVersion) {
            this.api = api;
            this.model = model;
            this.device = device;
            this.abi = abi;
            this.availableMemory = availableMemory;
            this.memoryThreshold = memoryThreshold;
            this.glEsVersion = glEsVersion;
        }

        public boolean isGles2Capable() {
            return glEsVersion >= 0x00020000;
        }

        @Override
        public String toString() {
            return "api=" + api + ",model=" + model + ",device=" + device + ",abi=" + abi
                    + ",availMem=" + availableMemory + ",threshold=" + memoryThreshold
                    + ",gles=" + Integer.toHexString(glEsVersion);
        }
    }
}
