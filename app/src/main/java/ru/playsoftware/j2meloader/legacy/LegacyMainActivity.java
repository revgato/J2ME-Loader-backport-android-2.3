/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;

/** Platform-widget launcher used on API 10; no SAF, fragments, database or notification channel. */
public final class LegacyMainActivity extends Activity {
    private static final int MENU_SETTINGS = 1;
    private final ArrayList<LegacyAppCatalog.Game> games = new ArrayList<LegacyAppCatalog.Game>();
    private LegacyGameGridAdapter gameAdapter;
    private FileLegacyAppCatalog appCatalog;
    private File emulatorDirectory;
    private AlertDialog conversionDialog;
    private TextView conversionStage;
    private TextView conversionLog;
    private ProgressBar conversionProgress;
    private Messenger conversionService;
    private boolean conversionBound;
    private boolean conversionTerminal;
    private File conversionResultDirectory;
    private String conversionResultName;

    private final Messenger conversionClient = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message message) {
            handleConversionMessage(message);
        }
    });

    private final ServiceConnection conversionConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, android.os.IBinder binder) {
            conversionService = new Messenger(binder);
            sendConversionStart();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            conversionService = null;
            conversionBound = false;
            if (!conversionTerminal) {
                appendConversionLog("ERROR", getString(R.string.conversion_worker_gone));
                finishConversion(false, null, null, getString(R.string.conversion_worker_gone));
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        emulatorDirectory = new File(Environment.getExternalStorageDirectory(), "J2ME-Loader");
        appCatalog = new FileLegacyAppCatalog(emulatorDirectory);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.background));

        root.addView(createToolbar(), new LinearLayout.LayoutParams(-1, dp(48)));

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
            showGameActions(games.get(position));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refreshCatalog();
    }

    @Override
    protected void onDestroy() {
        gameAdapter.close();
        if (conversionBound) {
            unbindService(conversionConnection);
            conversionBound = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, R.string.action_settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            startActivity(new Intent(this, LegacySettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(getResources().getColor(R.color.primary));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, dp(8), 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageButton install = toolbarAction(android.R.drawable.ic_menu_add,
                R.string.install_jar_jad);
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBrowser(Environment.getExternalStorageDirectory());
            }
        });
        toolbar.addView(install, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageButton settings = toolbarAction(android.R.drawable.ic_menu_preferences,
                R.string.action_settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(LegacyMainActivity.this, LegacySettingsActivity.class));
            }
        });
        toolbar.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return toolbar;
    }

    private void showGameActions(final LegacyAppCatalog.Game game) {
        new AlertDialog.Builder(this)
                .setTitle(game.getName())
                .setItems(new String[]{
                        getString(R.string.action_settings),
                        getString(R.string.action_context_delete)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startActivity(LegacyConfigActivity.createGameIntent(
                                    LegacyMainActivity.this, game.getDirectory(), game.getName()));
                        } else if (which == 1) {
                            confirmDelete(game);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final LegacyAppCatalog.Game game) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_context_delete)
                .setMessage(getString(R.string.message_delete) + "\n\n" + game.getName())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteGame(game);
                    }
                })
                .show();
    }

    private void deleteGame(LegacyAppCatalog.Game game) {
        try {
            appCatalog.delete(game);
            refreshCatalog();
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_disk_io, Toast.LENGTH_LONG).show();
        }
    }

    private ImageButton toolbarAction(int icon, int description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(getString(description));
        button.setBackgroundResource(R.drawable.legacy_toolbar_action_background);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private void refreshCatalog() {
        try {
            ArrayList<LegacyAppCatalog.Game> scanned = new ArrayList<LegacyAppCatalog.Game>(
                    appCatalog.scan());
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
        showConversionDialog(source);
    }

    private void showConversionDialog(final File source) {
        conversionTerminal = false;
        pendingSource = source;
        conversionResultDirectory = null;
        conversionResultName = null;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        conversionStage = new TextView(this);
        conversionStage.setText(R.string.conversion_stage_validating);
        content.addView(conversionStage, new LinearLayout.LayoutParams(-1, -2));
        conversionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        conversionProgress.setMax(100);
        conversionProgress.setProgress(0);
        content.addView(conversionProgress, new LinearLayout.LayoutParams(-1, dp(24)));
        ScrollView scroll = new ScrollView(this);
        conversionLog = new TextView(this);
        conversionLog.setTextSize(12);
        scroll.addView(conversionLog, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        conversionDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.conversion_title) + ": " + source.getName())
                .setView(content)
                .setNegativeButton(R.string.conversion_close, null)
                .setPositiveButton(R.string.conversion_play, null)
                .create();
        conversionDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                conversionDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                conversionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
            }
        });
        conversionDialog.setCancelable(false);
        conversionDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (conversionBound) {
                    unbindService(conversionConnection);
                    conversionBound = false;
                }
                conversionDialog = null;
            }
        });
        conversionDialog.show();

        Intent intent = new Intent(this, LegacyConversionService.class);
        conversionBound = bindService(intent, conversionConnection, Context.BIND_AUTO_CREATE);
        if (!conversionBound) {
            finishConversion(false, null, null, getString(R.string.conversion_worker_gone));
        }
    }

    private void sendConversionStart() {
        if (conversionService == null || conversionDialog == null) return;
        try {
            Message message = Message.obtain();
            message.what = LegacyConversionService.MSG_START;
            Bundle data = new Bundle();
            data.putString(LegacyConversionService.KEY_SOURCE_PATH, pendingSource.getCanonicalPath());
            message.setData(data);
            message.replyTo = conversionClient;
            conversionService.send(message);
        } catch (Exception e) {
            finishConversion(false, null, null, e.getMessage());
        }
    }

    private File pendingSource;

    private void handleConversionMessage(Message message) {
        Bundle data = message.getData();
        if (message.what == LegacyConversionService.MSG_LOG) {
            appendConversionLog(data.getString(LegacyConversionService.KEY_LEVEL),
                    data.getString(LegacyConversionService.KEY_TEXT));
        } else if (message.what == LegacyConversionService.MSG_PROGRESS) {
            String stage = data.getString(LegacyConversionService.KEY_STAGE);
            int percent = data.getInt(LegacyConversionService.KEY_PERCENT);
            if (conversionStage != null) conversionStage.setText(stageLabel(stage));
            if (conversionProgress != null) conversionProgress.setProgress(percent);
            String className = data.getString(LegacyConversionService.KEY_CLASS_NAME);
            if (className != null && className.length() > 0) {
                appendConversionLog("INFO", data.getInt(LegacyConversionService.KEY_COMPLETED)
                        + "/" + data.getInt(LegacyConversionService.KEY_TOTAL) + " " + className);
            }
        } else if (message.what == LegacyConversionService.MSG_RESULT) {
            String status = data.getString(LegacyConversionService.KEY_STATUS);
            boolean success = "INSTALLED".equals(status) || "UPDATED".equals(status);
            File directory = data.getString(LegacyConversionService.KEY_DIRECTORY) == null ? null
                    : new File(data.getString(LegacyConversionService.KEY_DIRECTORY));
            finishConversion(success, directory, data.getString(LegacyConversionService.KEY_NAME),
                    data.getString(LegacyConversionService.KEY_MESSAGE));
        }
    }

    private String stageLabel(String stage) {
        if ("validating".equals(stage)) return getString(R.string.conversion_stage_validating);
        if ("batching".equals(stage)) return getString(R.string.conversion_stage_batching);
        if ("converting".equals(stage)) return getString(R.string.conversion_stage_converting);
        if ("publishing".equals(stage)) return getString(R.string.conversion_stage_publishing);
        return stage == null ? "" : stage;
    }

    private void appendConversionLog(String level, String text) {
        if (conversionLog == null) return;
        conversionLog.append("[" + (level == null ? "INFO" : level) + "] "
                + (text == null ? "" : text) + "\n");
        final ScrollView parent = (ScrollView) conversionLog.getParent();
        if (parent != null) parent.post(new Runnable() {
            @Override public void run() { parent.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void finishConversion(boolean success, File directory, String name, String message) {
        if (conversionDialog == null || conversionTerminal) return;
        conversionTerminal = true;
        conversionResultDirectory = directory;
        conversionResultName = name;
        conversionDialog.setCancelable(true);
        if (conversionStage != null) conversionStage.setText(success
                ? R.string.conversion_success : R.string.conversion_failed);
        appendConversionLog(success ? "INFO" : "ERROR", success
                ? getString(R.string.conversion_success) : (message == null
                ? getString(R.string.conversion_failed) : message));
        conversionDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
        if (success) {
            refreshCatalog();
            conversionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
            conversionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (conversionResultDirectory != null) {
                        launch(new LegacyAppCatalog.Game(conversionResultDirectory.getName(),
                                conversionResultName, "", "", conversionResultDirectory));
                    }
                    conversionDialog.dismiss();
                }
            });
        }
    }

    private void launch(LegacyAppCatalog.Game game) {
        Intent intent = new Intent(this, MicroActivity.class);
        intent.setData(Uri.fromFile(game.getDirectory()));
        intent.putExtra(KEY_MIDLET_NAME, game.getName());
        startActivity(intent);
    }

}
