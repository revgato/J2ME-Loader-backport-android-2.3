/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfilesManager;

/** Initial native profile list; CRUD/editor actions are added in the next slice. */
public final class LegacyProfilesActivity extends Activity {
    public static Intent createIntent(Context context) {
        return new Intent(context, LegacyProfilesActivity.class);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.profiles);
        ListView list = new ListView(this);
        ArrayList<Profile> profiles = ProfilesManager.getProfiles();
        Collections.sort(profiles);
        ArrayAdapter<Profile> adapter = new ArrayAdapter<Profile>(this,
                android.R.layout.simple_list_item_1, profiles);
        list.setAdapter(adapter);
        setContentView(list);
    }
}
