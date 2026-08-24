/*
 * Copyright 2017-2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader;

import android.app.Application;
import android.content.Context;

import javax.microedition.util.ContextHolder;

/** Minimal application bootstrap. API 10 has no multidex, ACRA or night-mode setup. */
public class EmulatorApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ContextHolder.setApplication(this);
    }
}
