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
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.TextAppearanceSpan;
import android.util.SparseIntArray;
import android.view.View;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Profile;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;

/** API 10 game/profile editor. Changes remain a draft until Save is pressed. */
public final class LegacyConfigActivity extends Activity {
    private static final String EXTRA_PROFILE = "legacy.profile";
    private static final String EXTRA_PROFILE_NEW = "legacy.profile.new";
    private static final String EXTRA_GAME = "legacy.game";
    private static final String EXTRA_GAME_NAME = "legacy.game.name";

    private boolean isProfile;
    private boolean newProfile;
    private boolean loading;
    private boolean initialising = true;
    private boolean dirty;
    private String profileName;
    private String gameName;
    private File gameDir;
    private File configDir;
    private File keyboardSource;
    private ProfileModel params;

    private EditText width;
    private EditText height;
    private EditText scale;
    private EditText fps;
    private EditText fontSmall;
    private EditText fontMedium;
    private EditText fontLarge;
    private EditText systemProperties;
    private Spinner orientation;
    private Spinner scaleType;
    private Spinner gravity;
    private Spinner graphics;
    private Spinner keyboardType;
    private Spinner layout;
    private CheckBox filter;
    private CheckBox immediate;
    private CheckBox showFps;
    private CheckBox fullscreen;
    private CheckBox hwAcceleration;
    private CheckBox parallelRedraw;
    private CheckBox fontDimensions;
    private CheckBox antiAlias;
    private CheckBox touch;
    private CheckBox showKeyboard;
    private CheckBox keyboardFeedback;
    private CheckBox keyboardOpacity;

    public static Intent createProfileIntent(Context context, String name, boolean create) {
        Intent intent = new Intent(context, LegacyConfigActivity.class);
        intent.putExtra(EXTRA_PROFILE, name);
        intent.putExtra(EXTRA_PROFILE_NEW, create);
        return intent;
    }

    public static Intent createGameIntent(Context context, File directory, String name) {
        Intent intent = new Intent(context, LegacyConfigActivity.class);
        intent.putExtra(EXTRA_GAME, directory.getAbsolutePath());
        intent.putExtra(EXTRA_GAME_NAME, name);
        return intent;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!readTarget()) {
            finish();
            return;
        }
        // Widget callbacks (especially Spinner selection) may fire while the view tree is built.
        // Keep them out of the draft-change state until the file-backed values are loaded.
        loading = true;
        buildEditor();
        loadFromDisk();
    }

    private boolean readTarget() {
        Intent intent = getIntent();
        profileName = intent.getStringExtra(EXTRA_PROFILE);
        isProfile = profileName != null;
        newProfile = intent.getBooleanExtra(EXTRA_PROFILE_NEW, false);
        if (isProfile) {
            try {
                profileName = LegacyProfileName.normalize(profileName);
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                return false;
            }
            configDir = new File(Config.getProfilesDir(), profileName);
            setTitle(profileName);
            return LegacyProfileName.isChildOf(new File(Config.getProfilesDir()), configDir);
        }
        String path = intent.getStringExtra(EXTRA_GAME);
        if (path == null) {
            return false;
        }
        gameDir = new File(path);
        File converted = gameDir.getParentFile();
        File work = converted == null ? null : converted.getParentFile();
        if (!gameDir.isDirectory() || work == null) {
            return false;
        }
        gameName = intent.getStringExtra(EXTRA_GAME_NAME);
        if (gameName == null) {
            gameName = gameDir.getName();
        }
        configDir = new File(work, "configs" + File.separator + gameDir.getName());
        setTitle(gameName);
        return LegacyProfileName.isChildOf(new File(work, "configs"), configDir);
    }

    private void buildEditor() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusableInTouchMode(true);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        section(root, R.string.PREF_SCREEN_OPTIONS);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        width = edit(row, R.string.PREF_WIDTH, InputType.TYPE_CLASS_NUMBER);
        height = edit(row, R.string.PREF_HEIGHT, InputType.TYPE_CLASS_NUMBER);
        root.addView(row);
        scale = edit(root, R.string.PREF_SCALE_RATIO, InputType.TYPE_CLASS_NUMBER);
        orientation = spinner(root, R.string.PREF_ORIENTATION, R.array.PREF_ORIENTATION_ENTRIES);
        scaleType = spinner(root, R.string.pref_screen_scale_type, R.array.pref_scale_type_entries);
        gravity = spinner(root, R.string.pref_screen_gravity, R.array.pref_screen_gravity_entries);
        filter = check(root, R.string.PREF_FILTER, R.string.pref_legacy_config_filter_summary);
        immediate = check(root, R.string.PREF_IMMEDIATE, R.string.pref_legacy_config_immediate_summary);
        fullscreen = check(root, R.string.PREF_FORCE_FULLSCREEN, R.string.pref_legacy_config_fullscreen_summary);

        section(root, R.string.pref_graphics_mode_title);
        graphics = spinner(root, R.string.pref_graphics_mode_title, R.array.pref_graphics_mode_entries);
        hwAcceleration = check(root, R.string.PREF_HW_ACCELERATION,
                R.string.pref_legacy_config_hw_acceleration_summary);
        parallelRedraw = check(root, R.string.parallel_screen_redrawing,
                R.string.pref_legacy_config_parallel_redraw_summary);
        showFps = check(root, R.string.PREF_SHOW_FPS, R.string.pref_legacy_config_fps_summary);
        fps = edit(root, R.string.PREF_LIMIT_FPS, InputType.TYPE_CLASS_NUMBER);

        section(root, R.string.PREF_FONT_OPTIONS);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        fontSmall = edit(row, R.string.PREF_FONT_SMALL, InputType.TYPE_CLASS_NUMBER);
        fontMedium = edit(row, R.string.PREF_FONT_MEDIUM, InputType.TYPE_CLASS_NUMBER);
        fontLarge = edit(row, R.string.PREF_FONT_LARGE, InputType.TYPE_CLASS_NUMBER);
        root.addView(row);
        fontDimensions = check(root, R.string.PREF_FONT_SIZE_IN_SP,
                R.string.pref_legacy_config_font_sp_summary);
        antiAlias = check(root, R.string.PREF_FONT_ANTI_ALIASING,
                R.string.pref_legacy_config_font_antialias_summary);

        section(root, R.string.PREF_VIRTUAL_KEYBOARD_OPTIONS);
        touch = check(root, R.string.PREF_TOUCH_INPUT, R.string.pref_legacy_config_touch_summary);
        showKeyboard = check(root, R.string.PREF_VK_SHOW, R.string.pref_legacy_config_keyboard_summary);
        keyboardType = spinner(root, R.string.PREF_VK_TYPE, R.array.PREF_VK_TYPE_ENTRIES);
        layout = spinner(root, R.string.PREF_LAYOUT, R.array.PREF_LAYOUT_ENTRIES);
        keyboardFeedback = check(root, R.string.PREF_VK_FEEDBACK,
                R.string.pref_legacy_config_keyboard_feedback_summary);
        keyboardOpacity = check(root, R.string.PREF_VK_FORCE_OPACITY,
                R.string.pref_legacy_config_keyboard_opacity_summary);
        Button map = button(root, R.string.pref_map_keys);
        map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LegacyKeyMapperDialog.show(LegacyConfigActivity.this, params == null ? null : params.keyMappings,
                        new LegacyKeyMapperDialog.Callback() {
                            @Override
                            public void onMappingChanged(SparseIntArray mapping) {
                                if (params != null) {
                                    params.keyMappings = mapping;
                                    dirty = true;
                                }
                            }
                        });
            }
        });

        section(root, R.string.PREF_SYS_PROPS);
        systemProperties = new EditText(this);
        systemProperties.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
        systemProperties.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        systemProperties.setMinLines(5);
        systemProperties.setHint(R.string.PREF_SYS_PROPS_HINT);
        root.addView(systemProperties, new LinearLayout.LayoutParams(-1, dp(140)));

        if (!isProfile) {
            Button load = button(root, R.string.load_profile);
            load.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { showLoadProfile(); }
            });
            Button saveAs = button(root, R.string.save_profile);
            saveAs.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { showSaveAsProfile(); }
            });
        }
        Button reset = button(root, R.string.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                params = new ProfileModel(configDir);
                keyboardSource = existingKeyboard(configDir);
                loading = true;
                putParams();
                loading = false;
                dirty = true;
            }
        });
        Button save = button(root, R.string.save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { save(false); }
        });
        if (!isProfile) {
            Button play = button(root, R.string.START_CMD);
            play.setText(R.string.legacy_save_and_play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { save(true); }
            });
        }
        // Do not open the API 10 soft keyboard merely by entering the editor; physical Back
        // must remain the editor's discard/exit route until the user focuses a field.
        setContentView(scroll);
        root.requestFocus();
    }

    private void loadFromDisk() {
        loading = true;
        params = ProfilesManager.loadConfig(configDir);
        if (params == null && !isProfile) {
            try {
                String def = LegacyPreferences.get(this).getString(
                        ru.playsoftware.j2meloader.util.Constants.PREF_DEFAULT_PROFILE, null);
                LegacyProfileStore.applyDefaultIfMissing(configDir, def);
                params = ProfilesManager.loadConfig(configDir);
            } catch (IOException ignored) {
            }
        }
        if (params == null) {
            params = new ProfileModel(configDir);
        }
        params.dir = configDir;
        keyboardSource = existingKeyboard(configDir);
        putParams();
        // Keep initialization suppression active until the first window focus. On API 10
        // Spinner callbacks can be posted after setSelection() returns.
        loading = true;
        dirty = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && initialising) {
            // Let callbacks queued by Spinner.setSelection() and CheckBox.setChecked() drain
            // before enabling draft tracking. This is observable on the API 10 device.
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    if (initialising) {
                        initialising = false;
                        loading = false;
                        dirty = false;
                    }
                }
            });
        }
    }

    private void putParams() {
        width.setText(Integer.toString(params.screenWidth));
        height.setText(Integer.toString(params.screenHeight));
        scale.setText(Integer.toString(params.screenScaleRatio));
        fps.setText(params.fpsLimit == 0 ? "" : Integer.toString(params.fpsLimit));
        fontSmall.setText(Integer.toString(params.fontSizeSmall));
        fontMedium.setText(Integer.toString(params.fontSizeMedium));
        fontLarge.setText(Integer.toString(params.fontSizeLarge));
        select(orientation, params.orientation);
        select(scaleType, params.screenScaleType);
        select(gravity, params.screenGravity);
        select(graphics, params.graphicsMode);
        select(keyboardType, params.vkType);
        select(layout, params.keyCodesLayout);
        filter.setChecked(params.screenFilter);
        immediate.setChecked(params.immediateMode);
        showFps.setChecked(params.showFps);
        fullscreen.setChecked(params.forceFullscreen);
        hwAcceleration.setChecked(params.hwAcceleration);
        parallelRedraw.setChecked(params.parallelRedrawScreen);
        fontDimensions.setChecked(params.fontApplyDimensions);
        antiAlias.setChecked(params.fontAA);
        touch.setChecked(params.touchInput);
        showKeyboard.setChecked(params.showKeyboard);
        keyboardFeedback.setChecked(params.vkFeedback);
        keyboardOpacity.setChecked(params.vkForceOpacity);
        systemProperties.setText(params.systemProperties == null ? "" : params.systemProperties);
    }

    private void readParams() {
        params.screenWidth = integer(width, "Width");
        params.screenHeight = integer(height, "Height");
        params.screenScaleRatio = integer(scale, "Scale");
        params.fpsLimit = optionalInteger(fps, "FPS");
        params.fontSizeSmall = optionalInteger(fontSmall, "Small font");
        params.fontSizeMedium = optionalInteger(fontMedium, "Medium font");
        params.fontSizeLarge = optionalInteger(fontLarge, "Large font");
        LegacyConfigValidation.validateScreen(params.screenWidth, params.screenHeight);
        LegacyConfigValidation.validateScale(params.screenScaleRatio);
        LegacyConfigValidation.validateFps(params.fpsLimit);
        LegacyConfigValidation.validateFont(params.fontSizeSmall);
        LegacyConfigValidation.validateFont(params.fontSizeMedium);
        LegacyConfigValidation.validateFont(params.fontSizeLarge);
        params.orientation = orientation.getSelectedItemPosition();
        params.screenScaleType = scaleType.getSelectedItemPosition();
        params.screenGravity = gravity.getSelectedItemPosition();
        params.graphicsMode = graphics.getSelectedItemPosition();
        params.vkType = keyboardType.getSelectedItemPosition();
        params.keyCodesLayout = layout.getSelectedItemPosition();
        params.screenFilter = filter.isChecked();
        params.immediateMode = immediate.isChecked();
        params.showFps = showFps.isChecked();
        params.forceFullscreen = fullscreen.isChecked();
        params.hwAcceleration = hwAcceleration.isChecked();
        params.parallelRedrawScreen = parallelRedraw.isChecked();
        params.fontApplyDimensions = fontDimensions.isChecked();
        params.fontAA = antiAlias.isChecked();
        params.touchInput = touch.isChecked();
        params.showKeyboard = showKeyboard.isChecked();
        params.vkFeedback = keyboardFeedback.isChecked();
        params.vkForceOpacity = keyboardOpacity.isChecked();
        params.systemProperties = systemProperties.getText().toString();
        LegacyConfigValidation.validateSystemProperties(params.systemProperties);
    }

    private void save(boolean play) {
        try {
            readParams();
            if (!configDir.exists() && !configDir.mkdirs()) {
                throw new IOException("Cannot create " + configDir);
            }
            params.dir = configDir;
            if (!ProfilesManager.saveConfig(params)) {
                throw new IOException("Cannot write config.json");
            }
            if (keyboardSource != null && keyboardSource.isFile()
                    && !sameFile(keyboardSource, new File(configDir, Config.MIDLET_KEY_LAYOUT_FILE))) {
                LegacyProfileStore.copyKeyboardLayout(keyboardSource, configDir);
            }
            dirty = false;
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            if (play) {
                Intent intent = new Intent(this, MicroActivity.class);
                intent.setData(Uri.fromFile(gameDir));
                intent.putExtra(KEY_MIDLET_NAME, gameName);
                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showLoadProfile() {
        final ArrayList<Profile> profiles = ProfilesManager.getProfiles();
        Collections.sort(profiles);
        final String[] names = new String[profiles.size()];
        for (int i = 0; i < names.length; i++) names[i] = profiles.get(i).getName();
        new AlertDialog.Builder(this).setTitle(R.string.load_profile).setItems(names,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ProfileModel loaded = ProfilesManager.loadConfig(profiles.get(which).getDir());
                        if (loaded == null) {
                            Toast.makeText(LegacyConfigActivity.this, R.string.legacy_profile_no_config,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        params = loaded;
                        params.dir = configDir;
                        keyboardSource = existingKeyboard(profiles.get(which).getDir());
                        loading = true;
                        putParams();
                        loading = false;
                        dirty = true;
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showSaveAsProfile() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle(R.string.enter_name).setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name;
                        try {
                            name = LegacyProfileName.normalize(input.getText().toString());
                        } catch (IllegalArgumentException e) {
                            Toast.makeText(LegacyConfigActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (hasExistingProfile(name)) {
                            Toast.makeText(LegacyConfigActivity.this, R.string.error_name_exists,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            readParams();
                            File target = new File(Config.getProfilesDir(), name);
                            if (!LegacyProfileName.isChildOf(new File(Config.getProfilesDir()), target)
                                    || !target.mkdirs()) throw new IOException("Cannot create profile");
                            ProfileModel copy = params;
                            copy.dir = target;
                            if (!ProfilesManager.saveConfig(copy)) throw new IOException("Cannot save profile");
                            if (keyboardSource != null && keyboardSource.isFile()) {
                                LegacyProfileStore.copyKeyboardLayout(keyboardSource, target);
                            }
                            params.dir = configDir;
                            Toast.makeText(LegacyConfigActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(LegacyConfigActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    @Override
    public void onBackPressed() {
        if (!dirty) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.warning)
                .setMessage(R.string.legacy_discard_changes)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LegacyConfigActivity.super.onBackPressed();
                    }
                }).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // API 10's EditText/IME can consume Back before Activity.onBackPressed(). Keep the
        // editor's discard guard reachable from the physical Back key as well.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private EditText edit(LinearLayout parent, int label, int inputType) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(label);
        box.addView(title);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(inputType);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        }
        field.addTextChangedListener(watcher);
        return field;
    }

    private Spinner spinner(LinearLayout parent, int label, int entries) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(label);
        box.addView(title);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, entries,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        box.addView(spinner);
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { markDirty(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        return spinner;
    }

    private CheckBox check(LinearLayout parent, int label, int summary) {
        CheckBox box = new CheckBox(this);
        SpannableStringBuilder text = new SpannableStringBuilder(getString(label));
        if (summary != 0) {
            text.append('\n');
            int start = text.length();
            text.append(getString(summary));
            text.setSpan(new TextAppearanceSpan(this, android.R.style.TextAppearance_Small),
                    start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        box.setText(text);
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) { markDirty(); }
        });
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private Button button(LinearLayout parent, int label) {
        Button button = new Button(this);
        button.setText(label);
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void section(LinearLayout parent, int label) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setPadding(0, dp(12), 0, dp(4));
        parent.addView(title);
    }

    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
        @Override public void onTextChanged(CharSequence s, int st, int before, int count) { markDirty(); }
        @Override public void afterTextChanged(Editable s) { }
    };

    private void markDirty() {
        if (!loading && !initialising) dirty = true;
    }

    private int integer(EditText field, String name) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private int optionalInteger(EditText field, String name) {
        String value = field.getText().toString().trim();
        return value.length() == 0 ? 0 : integer(field, name);
    }

    private void select(Spinner spinner, int value) {
        if (spinner.getAdapter() != null && spinner.getAdapter().getCount() > 0) {
            spinner.setSelection(Math.max(0, Math.min(value, spinner.getAdapter().getCount() - 1)));
        }
    }

    private File existingKeyboard(File dir) {
        File file = new File(dir, Config.MIDLET_KEY_LAYOUT_FILE);
        return file.isFile() ? file : null;
    }

    private boolean hasExistingProfile(String name) {
        ArrayList<Profile> profiles = ProfilesManager.getProfiles();
        for (Profile profile : profiles) {
            if (LegacyProfileName.isSame(profile.getName(), name)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException e) {
            return left.equals(right);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
