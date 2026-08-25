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
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Map;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.keyboard.KeyMapper;

import ru.playsoftware.j2meloader.R;

/** API 10-safe key mapping screen with the same grid flow as the current app. */
public final class LegacyKeyMapperActivity extends Activity implements View.OnClickListener {
    public static final String EXTRA_PHYSICAL_KEYS = "legacy.mapper.physical.keys";
    public static final String EXTRA_CANVAS_KEYS = "legacy.mapper.canvas.keys";
    public static final String EXTRA_USE_DEFAULTS = "legacy.mapper.use.defaults";

    private static final String STATE_PHYSICAL_KEYS = "legacy.mapper.state.physical.keys";
    private static final String STATE_CANVAS_KEYS = "legacy.mapper.state.canvas.keys";
    private static final int NO_KEY = Integer.MIN_VALUE;

    private final SparseIntArray viewToCanvasKey = new SparseIntArray();
    private final Rect popupRect = new Rect();
    private LegacyKeyMapping defaultMapping;
    private LegacyKeyMapping mapping;
    private FrameLayout mappingLayer;
    private View mappingPopup;
    private TextView mappingMessage;
    private int canvasKey = NO_KEY;

    public static Intent createIntent(Context context, SparseIntArray current) {
        Intent intent = new Intent(context, LegacyKeyMapperActivity.class);
        if (current != null) {
            int[] physicalKeys = new int[current.size()];
            int[] canvasKeys = new int[current.size()];
            for (int i = 0; i < current.size(); i++) {
                physicalKeys[i] = current.keyAt(i);
                canvasKeys[i] = current.valueAt(i);
            }
            intent.putExtra(EXTRA_PHYSICAL_KEYS, physicalKeys);
            intent.putExtra(EXTRA_CANVAS_KEYS, canvasKeys);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.legacy_key_mappings);
        setContentView(R.layout.activity_legacy_key_mapper);

        defaultMapping = fromSparse(KeyMapper.getDefaultKeyMap());
        mapping = restoreMapping(state);
        mappingLayer = (FrameLayout) findViewById(R.id.legacy_key_mapper_layer);
        mappingPopup = findViewById(R.id.legacy_key_mapper_popup);
        mappingMessage = (TextView) findViewById(R.id.legacy_key_mapper_message);
        mappingPopup.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) { return true; }
        });

        bindButton(R.id.virtual_key_left_soft, Canvas.KEY_SOFT_LEFT);
        bindButton(R.id.virtual_key_right_soft, Canvas.KEY_SOFT_RIGHT);
        bindButton(R.id.virtual_key_d, Canvas.KEY_SEND);
        bindButton(R.id.virtual_key_c, Canvas.KEY_END);
        bindButton(R.id.virtual_key_left, Canvas.KEY_LEFT);
        bindButton(R.id.virtual_key_right, Canvas.KEY_RIGHT);
        bindButton(R.id.virtual_key_up, Canvas.KEY_UP);
        bindButton(R.id.virtual_key_down, Canvas.KEY_DOWN);
        bindButton(R.id.virtual_key_f, Canvas.KEY_FIRE);
        bindButton(R.id.virtual_key_1, Canvas.KEY_NUM1);
        bindButton(R.id.virtual_key_2, Canvas.KEY_NUM2);
        bindButton(R.id.virtual_key_3, Canvas.KEY_NUM3);
        bindButton(R.id.virtual_key_4, Canvas.KEY_NUM4);
        bindButton(R.id.virtual_key_5, Canvas.KEY_NUM5);
        bindButton(R.id.virtual_key_6, Canvas.KEY_NUM6);
        bindButton(R.id.virtual_key_7, Canvas.KEY_NUM7);
        bindButton(R.id.virtual_key_8, Canvas.KEY_NUM8);
        bindButton(R.id.virtual_key_9, Canvas.KEY_NUM9);
        bindButton(R.id.virtual_key_0, Canvas.KEY_NUM0);
        bindButton(R.id.virtual_key_star, Canvas.KEY_STAR);
        bindButton(R.id.virtual_key_pound, Canvas.KEY_POUND);
        bindButton(R.id.virtual_key_a, KeyMapper.SE_KEY_SPECIAL_GAMING_A);
        bindButton(R.id.virtual_key_b, KeyMapper.SE_KEY_SPECIAL_GAMING_B);
        bindButton(R.id.virtual_key_menu, KeyMapper.KEY_OPTIONS_MENU);

        findViewById(R.id.legacy_key_mapper_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                cancelCapture();
                mapping.reset(defaultMapping);
            }
        });
        findViewById(R.id.legacy_key_mapper_done).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finishWithResult(); }
        });
    }

    private LegacyKeyMapping restoreMapping(Bundle state) {
        int[] physicalKeys = state == null ? null : state.getIntArray(STATE_PHYSICAL_KEYS);
        int[] canvasKeys = state == null ? null : state.getIntArray(STATE_CANVAS_KEYS);
        if (physicalKeys == null || canvasKeys == null) {
            Intent intent = getIntent();
            physicalKeys = intent.getIntArrayExtra(EXTRA_PHYSICAL_KEYS);
            canvasKeys = intent.getIntArrayExtra(EXTRA_CANVAS_KEYS);
        }
        if (physicalKeys == null && canvasKeys == null) {
            return defaultMapping.copy();
        }
        try {
            return LegacyKeyMapping.fromArrays(physicalKeys, canvasKeys);
        } catch (IllegalArgumentException ignored) {
            return defaultMapping.copy();
        }
    }

    private void bindButton(int id, int key) {
        viewToCanvasKey.put(id, key);
        findViewById(id).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        int key = viewToCanvasKey.get(view.getId(), NO_KEY);
        if (key != NO_KEY) {
            showMapping(key);
        }
    }

    private void showMapping(int key) {
        canvasKey = key;
        int physicalKey = mapping.physicalKeyFor(key, NO_KEY);
        String current = physicalKey == NO_KEY
                ? getString(R.string.mapping_dialog_key_not_specified)
                : physicalName(physicalKey);
        mappingMessage.setText(getString(R.string.mapping_dialog_message, current));
        mappingLayer.setVisibility(View.VISIBLE);
        mappingPopup.requestFocus();
    }

    private void cancelCapture() {
        canvasKey = NO_KEY;
        if (mappingLayer != null) {
            mappingLayer.setVisibility(View.GONE);
        }
    }

    private void finishWithResult() {
        if (mapping.hasCanvasKey(KeyMapper.KEY_OPTIONS_MENU)) {
            returnResult();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.alert_map_menu)
                .setNegativeButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        returnResult();
                    }
                })
                .setPositiveButton(R.string.CANCEL_CMD, null)
                .show();
    }

    private void returnResult() {
        Intent result = new Intent();
        if (mapping.equals(defaultMapping)) {
            result.putExtra(EXTRA_USE_DEFAULTS, true);
        } else {
            result.putExtra(EXTRA_PHYSICAL_KEYS, mapping.physicalKeys());
            result.putExtra(EXTRA_CANVAS_KEYS, mapping.canvasKeys());
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private String physicalName(int keyCode) {
        Is14shKeyProfile profile = Is14shKeyProfile.forDevice(Build.MODEL, Build.DEVICE);
        if (profile != null) {
            Map<Integer, String> names = profile.getKeyNames();
            String name = names.get(keyCode);
            if (name != null) {
                return name;
            }
        }
        return Integer.toString(keyCode);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mappingLayer != null && mappingLayer.getVisibility() == View.VISIBLE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_HOME
                        || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    return super.dispatchKeyEvent(event);
                }
                mapping.assign(keyCode, canvasKey);
                cancelCapture();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mappingLayer != null && mappingLayer.getVisibility() == View.VISIBLE
                && event.getAction() == MotionEvent.ACTION_DOWN) {
            mappingPopup.getGlobalVisibleRect(popupRect);
            if (!popupRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                cancelCapture();
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mappingLayer != null && mappingLayer.getVisibility() == View.VISIBLE) {
            return;
        }
        finishWithResult();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putIntArray(STATE_PHYSICAL_KEYS, mapping.physicalKeys());
        state.putIntArray(STATE_CANVAS_KEYS, mapping.canvasKeys());
        super.onSaveInstanceState(state);
    }

    private LegacyKeyMapping fromSparse(SparseIntArray sparse) {
        int[] physicalKeys = new int[sparse.size()];
        int[] canvasKeys = new int[sparse.size()];
        for (int i = 0; i < sparse.size(); i++) {
            physicalKeys[i] = sparse.keyAt(i);
            canvasKeys[i] = sparse.valueAt(i);
        }
        return LegacyKeyMapping.fromArrays(physicalKeys, canvasKeys);
    }
}
