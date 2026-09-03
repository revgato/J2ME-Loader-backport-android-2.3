/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

/** Receives conversion progress without depending on Android UI classes. */
public interface InstallProgressListener {
    void onStage(String stage);

    void onProgress(int completed, int total, String className);

    void onLog(String level, String message);
}
