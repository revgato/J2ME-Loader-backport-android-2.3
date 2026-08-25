/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;

/** Platform-widget launcher used on API 10; no SAF, fragments, database or notification channel. */
public final class LegacyMainActivity extends Activity {
    private static final int MENU_SETTINGS = 1;
    private static final int MENU_PROFILES = 2;
    private final ExecutorService installerExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<LegacyAppCatalog.Game> games = new ArrayList<LegacyAppCatalog.Game>();
    private LegacyGameGridAdapter gameAdapter;
    private File emulatorDirectory;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        emulatorDirectory = new File(Environment.getExternalStorageDirectory(), "J2ME-Loader");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.background));

        Button install = new Button(this);
        install.setText("Install JAR/JAD");
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBrowser(Environment.getExternalStorageDirectory());
            }
        });
        root.addView(install, new LinearLayout.LayoutParams(-1, -2));

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(112));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        grid.setClipToPadding(false);
        grid.setSelector(R.drawable.legacy_game_tile_background);
        grid.setDrawSelectorOnTop(false);
        gameAdapter = new LegacyGameGridAdapter(this);
        grid.setAdapter(gameAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(games.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            LegacyAppCatalog.Game game = games.get(position);
            startActivity(LegacyConfigActivity.createGameIntent(this,
                    game.getDirectory(), game.getName()));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refreshCatalog();
    }

    @Override
    protected void onDestroy() {
        gameAdapter.close();
        installerExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, R.string.action_settings);
        menu.add(0, MENU_PROFILES, 1, R.string.profiles);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            startActivity(new Intent(this, LegacySettingsActivity.class));
            return true;
        }
        if (item.getItemId() == MENU_PROFILES) {
            startActivity(LegacyProfilesActivity.createIntent(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshCatalog() {
        try {
            ArrayList<LegacyAppCatalog.Game> scanned = new ArrayList<LegacyAppCatalog.Game>(
                    new FileLegacyAppCatalog(emulatorDirectory).scan());
            games.clear();
            games.addAll(scanned);
            gameAdapter.setGames(scanned);
        } catch (Exception e) {
            Toast.makeText(this, "Catalog: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showBrowser(final File directory) {
        final File[] files = directory.listFiles();
        if (files == null) {
            Toast.makeText(this, "Cannot read " + directory, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<File> visible = new ArrayList<File>();
        for (File file : files) {
            if (file.isDirectory() || isInstallable(file)) {
                visible.add(file);
            }
        }
        Collections.sort(visible, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        boolean canGoUp = canNavigateToParent(directory);
        final String[] labels = new String[visible.size() + (canGoUp ? 1 : 0)];
        final int offset = canGoUp ? 1 : 0;
        if (canGoUp) {
            labels[0] = "..";
        }
        for (int i = 0; i < visible.size(); i++) {
            labels[i + offset] = visible.get(i).getName();
        }
        new AlertDialog.Builder(this)
                .setTitle(directory.getAbsolutePath())
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File selected;
                        if (canGoUp && which == 0) {
                            selected = directory.getParentFile();
                        } else {
                            int index = which - offset;
                            if (index < 0 || index >= visible.size()) {
                                return;
                            }
                            selected = visible.get(index);
                        }
                        if (selected.isDirectory() && isWithinStorageRoot(selected)) {
                            showBrowser(selected);
                        } else if (selected.isFile() && isInstallable(selected)) {
                            install(selected);
                        } else {
                            Toast.makeText(LegacyMainActivity.this,
                                    "Path is outside the SD-card root", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static boolean isInstallable(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".jad");
    }

    private static boolean canNavigateToParent(File directory) {
        try {
            String root = Environment.getExternalStorageDirectory().getCanonicalPath();
            String current = directory.getCanonicalPath();
            return current.length() > root.length() && current.startsWith(root + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isWithinStorageRoot(File file) {
        try {
            String root = Environment.getExternalStorageDirectory().getCanonicalPath();
            String path = file.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void install(final File source) {
        if (!isWithinStorageRoot(source)) {
            Toast.makeText(this, "Only files on the SD-card are supported", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Installing " + source.getName(), Toast.LENGTH_SHORT).show();
        installerExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final InstallResult result = new LegacyInstaller(emulatorDirectory).install(source);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LegacyMainActivity.this,
                                result.isSuccess() ? "Installed " + result.getName()
                                        : result.getMessage(), Toast.LENGTH_LONG).show();
                        if (result.isSuccess()) {
                            refreshCatalog();
                        }
                    }
                });
            }
        });
    }

    private void launch(LegacyAppCatalog.Game game) {
        Intent intent = new Intent(this, MicroActivity.class);
        intent.setData(Uri.fromFile(game.getDirectory()));
        intent.putExtra(KEY_MIDLET_NAME, game.getName());
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Keep the profile discoverable and leave the actual remap to the game Canvas.
        Is14shKeyProfile profile = Is14shKeyProfile.forDevice(android.os.Build.MODEL,
                android.os.Build.DEVICE);
        if (profile != null && profile.getKeyNames().containsKey(keyCode)) {
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
}
