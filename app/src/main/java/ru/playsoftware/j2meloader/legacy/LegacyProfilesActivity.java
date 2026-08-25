/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;

import static ru.playsoftware.j2meloader.util.Constants.PREF_DEFAULT_PROFILE;

/** Native API 10 profile manager. Long-press a profile for its actions. */
public final class LegacyProfilesActivity extends Activity {
    private static final int MENU_ADD = 1;
    private static final int ACTION_EDIT = 2;
    private static final int ACTION_RENAME = 3;
    private static final int ACTION_DELETE = 4;
    private static final int ACTION_DEFAULT = 5;
    private static final int ACTION_CLEAR_DEFAULT = 6;

    private final ArrayList<Profile> profiles = new ArrayList<Profile>();
    private ArrayAdapter<String> adapter;
    private ListView listView;

    public static Intent createIntent(Context context) {
        return new Intent(context, LegacyProfilesActivity.class);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.profiles);
        listView = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                listView.showContextMenuForChild(view);
            }
        });
        registerForContextMenu(listView);
        setContentView(listView);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            refresh();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ADD, 0, R.string.add);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_ADD) {
            promptName(null, false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        AdapterView.AdapterContextMenuInfo item = (AdapterView.AdapterContextMenuInfo) info;
        Profile profile = profiles.get(item.position);
        menu.setHeaderTitle(profile.getName());
        menu.add(0, ACTION_EDIT, 0, R.string.edit);
        menu.add(0, ACTION_RENAME, 1, R.string.action_context_rename);
        menu.add(0, ACTION_DELETE, 2, R.string.action_context_delete);
        String current = LegacyPreferences.get(this).getString(PREF_DEFAULT_PROFILE, null);
        if (profile.getName().equals(current)) {
            menu.add(0, ACTION_CLEAR_DEFAULT, 3, R.string.legacy_clear_default);
        } else {
            menu.add(0, ACTION_DEFAULT, 3, R.string.set_as_default);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null || info.position < 0 || info.position >= profiles.size()) {
            return false;
        }
        final Profile profile = profiles.get(info.position);
        switch (item.getItemId()) {
            case ACTION_EDIT:
                startActivity(LegacyConfigActivity.createProfileIntent(this, profile.getName(), false));
                return true;
            case ACTION_RENAME:
                promptName(profile, true);
                return true;
            case ACTION_DELETE:
                confirmDelete(profile);
                return true;
            case ACTION_DEFAULT:
                setDefault(profile.getName());
                return true;
            case ACTION_CLEAR_DEFAULT:
                LegacyPreferences.get(this).edit().remove(PREF_DEFAULT_PROFILE).apply();
                refresh();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void refresh() {
        profiles.clear();
        profiles.addAll(ProfilesManager.getProfiles());
        Collections.sort(profiles);
        adapter.clear();
        String current = LegacyPreferences.get(this).getString(PREF_DEFAULT_PROFILE, null);
        for (Profile profile : profiles) {
            adapter.add(profile.getName().equals(current)
                    ? profile.getName() + " (default)" : profile.getName());
        }
        adapter.notifyDataSetChanged();
    }

    private void setDefault(String name) {
        LegacyPreferences.get(this).edit().putString(PREF_DEFAULT_PROFILE, name).apply();
        refresh();
    }

    private void promptName(final Profile oldProfile, final boolean rename) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        if (oldProfile != null) {
            input.setText(oldProfile.getName());
            input.setSelection(input.length());
        }
        new AlertDialog.Builder(this)
                .setTitle(rename ? R.string.enter_new_name : R.string.enter_name)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name;
                        try {
                            name = LegacyProfileName.normalize(input.getText().toString());
                        } catch (IllegalArgumentException e) {
                            Toast.makeText(LegacyProfilesActivity.this, e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (hasDuplicate(name, oldProfile)) {
                            Toast.makeText(LegacyProfilesActivity.this,
                                    R.string.error_name_exists, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (rename) {
                            rename(oldProfile, name);
                        } else {
                            startActivity(LegacyConfigActivity.createProfileIntent(
                                    LegacyProfilesActivity.this, name, true));
                        }
                    }
                }).show();
    }

    private boolean hasDuplicate(String name, Profile except) {
        for (Profile profile : profiles) {
            if (profile != except && LegacyProfileName.isSame(profile.getName(), name)) {
                return true;
            }
        }
        File root = new File(Config.getProfilesDir());
        File candidate = new File(root, name);
        return except == null && candidate.exists();
    }

    private void rename(Profile profile, String name) {
        File root = new File(Config.getProfilesDir());
        File oldDir = profile.getDir();
        File newDir = new File(root, name);
        try {
            if (!LegacyProfileName.isChildOf(root, oldDir)
                    || !LegacyProfileName.isChildOf(root, newDir)
                    || !oldDir.isDirectory() || newDir.exists()
                    || !oldDir.renameTo(newDir)) {
                throw new IllegalArgumentException(getString(R.string.legacy_cannot_rename));
            }
            String current = LegacyPreferences.get(this).getString(PREF_DEFAULT_PROFILE, null);
            if (profile.getName().equals(current)) {
                LegacyPreferences.get(this).edit().putString(PREF_DEFAULT_PROFILE, name).apply();
            }
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(final Profile profile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_context_delete)
                .setMessage(getString(R.string.legacy_delete_profile, profile.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File root = new File(Config.getProfilesDir());
                        if (!LegacyProfileName.isChildOf(root, profile.getDir())) {
                            return;
                        }
                        FileUtils.deleteDirectory(profile.getDir());
                        String current = LegacyPreferences.get(LegacyProfilesActivity.this)
                                .getString(PREF_DEFAULT_PROFILE, null);
                        if (profile.getName().equals(current)) {
                            LegacyPreferences.get(LegacyProfilesActivity.this).edit()
                                    .remove(PREF_DEFAULT_PROFILE).apply();
                        }
                        refresh();
                    }
                }).show();
    }
}
