/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared preference access for the API 10 shell. */
public final class LegacyPreferences {
    public static final String NAME = "legacy-preferences";

    private LegacyPreferences() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
