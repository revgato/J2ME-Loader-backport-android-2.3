/*
 * Copyright 2015-2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package javax.microedition.shell;

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.KEY_START_ARGUMENTS;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.LinkedHashMap;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;

/** Single-process platform Activity for the API 10 MIDlet runtime. */
public class MicroActivity extends Activity {
    private static final int ORIENTATION_DEFAULT = 0;
    private static final int ORIENTATION_AUTO = 1;
    private static final int ORIENTATION_PORTRAIT = 2;
    private static final int ORIENTATION_LANDSCAPE = 3;

    public ActivityMicroBinding binding;
    private Displayable current;
    private boolean visible;
    private MicroLoader microLoader;
    private String appName;
    private String appPath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ContextHolder.setCurrentActivity(this);
        binding = ActivityMicroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        android.content.Intent intent = getIntent();
        appName = intent.getStringExtra(KEY_MIDLET_NAME);
        if (appName == null) {
            appName = "MIDlet";
        }
        if (intent.getData() == null || intent.getData().getPath() == null) {
            showErrorDialog("Invalid intent: app path is null");
            return;
        }
        appPath = intent.getData().getPath();
        microLoader = new MicroLoader(this, appPath);
        if (!microLoader.init()) {
            showErrorDialog("MIDlet configuration is missing");
            return;
        }
        microLoader.applyConfiguration();
        VirtualKeyboard keyboard = ContextHolder.getVk();
        if (keyboard != null) {
            keyboard.setView(binding.overlayView);
            binding.overlayView.addLayer(keyboard);
        }
        setOrientation(microLoader.getOrientation());
        try {
            loadMIDlet();
        } catch (Exception e) {
            showErrorDialog(e.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        MidletThread.resumeApp();
    }

    @Override
    protected void onPause() {
        visible = false;
        MidletThread.pauseApp();
        super.onPause();
    }

    private void loadMIDlet() throws Exception {
        LinkedHashMap<String, String> midlets = microLoader.loadMIDletList();
        if (midlets.size() == 0) {
            throw new Exception("No MIDlets found");
        }
        String[] classes = midlets.keySet().toArray(new String[midlets.size()]);
        String[] names = midlets.values().toArray(new String[midlets.size()]);
        if (classes.length == 1) {
            MidletThread.create(microLoader, classes[0]);
        } else {
            showMidletDialog(names, classes);
        }
    }

    private void showMidletDialog(String[] names, final String[] classes) {
        new AlertDialog.Builder(this)
                .setTitle("Select MIDlet")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MidletThread.create(microLoader, classes[which]);
                        MidletThread.resumeApp();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        MidletThread.notifyDestroyed();
                    }
                }).show();
    }

    void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).show();
    }

    private void setOrientation(int orientation) {
        switch (orientation) {
            case ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case ORIENTATION_AUTO:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    public void setCurrent(Displayable displayable) {
        ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
        current = displayable;
    }

    public Displayable getCurrent() {
        return current;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getAppName() {
        return appName;
    }

    public void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Exit MIDlet?")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MidletThread.destroyApp();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (binding != null && binding.displayableContainer.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        showExitConfirmation();
    }

    private final class SetCurrentEvent extends SimpleEvent {
        private final Displayable next;

        SetCurrentEvent(Displayable previous, Displayable next) {
            this.next = next;
        }

        @Override
        public void process() {
            if (current != null) {
                current.clearDisplayableView();
            }
            if (next instanceof Alert) {
                return;
            }
            binding.displayableContainer.removeAllViews();
            binding.overlayView.setVisibility(next instanceof Canvas);
            if (next != null) {
                String title = next.getTitle();
                binding.toolbar.setText(title == null ? appName : title);
                binding.toolbar.setVisibility(next instanceof Canvas ? View.GONE : View.VISIBLE);
                binding.displayableContainer.addView(next.getDisplayableView());
            }
        }
    }

    @Override
    protected void onDestroy() {
        MidletThread.notifyDestroyed();
        binding = null;
        super.onDestroy();
    }
}
