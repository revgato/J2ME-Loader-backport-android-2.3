/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;

import static ru.playsoftware.j2meloader.util.Constants.PREF_KEEP_SCREEN;
import static ru.playsoftware.j2meloader.util.Constants.PREF_STATUSBAR;
import static ru.playsoftware.j2meloader.util.Constants.PREF_VIBRATION;

/** Native preference screen kept compatible with Android 2.3. */
public final class LegacySettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.action_settings);
        getPreferenceManager().setSharedPreferencesName(LegacyPreferences.NAME);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        PreferenceCategory runtime = new PreferenceCategory(this);
        runtime.setTitle(R.string.pref_legacy_runtime_category);
        screen.addPreference(runtime);
        runtime.addPreference(checkBox(PREF_KEEP_SCREEN, R.string.pref_wakelock_title,
                R.string.pref_legacy_keep_screen_summary, false));
        runtime.addPreference(checkBox(PREF_STATUSBAR, R.string.pref_enable_statusbar_title,
                R.string.pref_legacy_statusbar_summary, false));
        runtime.addPreference(checkBox(PREF_VIBRATION, R.string.pref_vibration_title,
                R.string.pref_legacy_vibration_summary, true));

        PreferenceCategory storage = new PreferenceCategory(this);
        storage.setTitle(R.string.pref_legacy_storage_category);
        screen.addPreference(storage);
        Preference workDir = new Preference(this);
        workDir.setTitle(R.string.pref_emulator_dir);
        workDir.setSummary(Config.getEmulatorDir());
        workDir.setSelectable(false);
        storage.addPreference(workDir);

        Preference profiles = new Preference(this);
        profiles.setTitle(R.string.profiles);
        profiles.setSummary(R.string.pref_legacy_profiles_summary);
        profiles.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(LegacyProfilesActivity.createIntent(LegacySettingsActivity.this));
                return true;
            }
        });
        storage.addPreference(profiles);
        setPreferenceScreen(screen);
    }

    private CheckBoxPreference checkBox(String key, int title, int summary, boolean defaultValue) {
        CheckBoxPreference preference = new CheckBoxPreference(this);
        preference.setKey(key);
        preference.setTitle(title);
        if (summary != 0) {
            preference.setSummary(summary);
        }
        preference.setDefaultValue(defaultValue);
        return preference;
    }
}
