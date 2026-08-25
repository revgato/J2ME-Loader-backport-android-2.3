/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.microedition.lcdui.Canvas;

import ru.playsoftware.j2meloader.R;

/** API 10 key mapper: select a MIDP key, then press a physical device key. */
public final class LegacyKeyMapperDialog {
    public interface Callback {
        void onMappingChanged(SparseIntArray mapping);
    }

    private static final int[] TARGETS = {
            0, Canvas.KEY_SOFT_LEFT, Canvas.KEY_SOFT_RIGHT, Canvas.KEY_UP,
            Canvas.KEY_DOWN, Canvas.KEY_LEFT, Canvas.KEY_RIGHT, Canvas.KEY_FIRE,
            Canvas.KEY_NUM0, Canvas.KEY_NUM1, Canvas.KEY_NUM2, Canvas.KEY_NUM3,
            Canvas.KEY_NUM4, Canvas.KEY_NUM5, Canvas.KEY_NUM6, Canvas.KEY_NUM7,
            Canvas.KEY_NUM8, Canvas.KEY_NUM9, Canvas.KEY_STAR, Canvas.KEY_POUND,
            Canvas.GAME_A, Canvas.GAME_B, Canvas.GAME_C, Canvas.GAME_D
    };

    private LegacyKeyMapperDialog() {
    }

    public static void show(final Activity activity, SparseIntArray current,
                            final Callback callback) {
        final SparseIntArray mapping = cloneMap(current);
        final String[] labels = new String[TARGETS.length];
        for (int i = 0; i < TARGETS.length; i++) {
            labels[i] = targetName(TARGETS[i]) + "  [" + physicalName(mapping, TARGETS[i]) + "]";
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.legacy_key_mappings)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        capture(activity, mapping, TARGETS[which], callback);
                    }
                })
                .setNegativeButton("Reset", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onMappingChanged(new SparseIntArray());
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void capture(Activity activity, final SparseIntArray mapping,
                                final int target, final Callback callback) {
        final AlertDialog capture = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.legacy_key_capture, targetName(target)))
                .setMessage(R.string.legacy_key_capture_hint)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        capture.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                    dialog.dismiss();
                    return true;
                }
                for (int i = mapping.size() - 1; i >= 0; i--) {
                    if (mapping.valueAt(i) == target || mapping.keyAt(i) == keyCode) {
                        mapping.removeAt(i);
                    }
                }
                mapping.put(keyCode, target);
                dialog.dismiss();
                callback.onMappingChanged(cloneMap(mapping));
                return true;
            }
        });
        capture.show();
    }

    private static SparseIntArray cloneMap(SparseIntArray source) {
        SparseIntArray result = new SparseIntArray();
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                result.put(source.keyAt(i), source.valueAt(i));
            }
        }
        return result;
    }

    private static String physicalName(SparseIntArray mapping, int target) {
        Is14shKeyProfile profile = Is14shKeyProfile.forDevice("IS14SH", "SHI14");
        Map<Integer, String> names = profile == null
                ? new LinkedHashMap<Integer, String>() : profile.getKeyNames();
        for (int i = 0; i < mapping.size(); i++) {
            if (mapping.valueAt(i) == target) {
                String name = names.get(mapping.keyAt(i));
                return name == null ? Integer.toString(mapping.keyAt(i)) : name;
            }
        }
        return "not specified";
    }

    private static String targetName(int target) {
        switch (target) {
            case 0: return "Menu";
            case Canvas.KEY_SOFT_LEFT: return "Soft left";
            case Canvas.KEY_SOFT_RIGHT: return "Soft right";
            case Canvas.KEY_UP: return "Up";
            case Canvas.KEY_DOWN: return "Down";
            case Canvas.KEY_LEFT: return "Left";
            case Canvas.KEY_RIGHT: return "Right";
            case Canvas.KEY_FIRE: return "Fire";
            case Canvas.KEY_STAR: return "Star";
            case Canvas.KEY_POUND: return "Pound";
            case Canvas.GAME_A: return "Game A";
            case Canvas.GAME_B: return "Game B";
            case Canvas.GAME_C: return "Game C";
            case Canvas.GAME_D: return "Game D";
            default:
                if (target >= Canvas.KEY_NUM0 && target <= Canvas.KEY_NUM9) {
                    return "Number " + (target - Canvas.KEY_NUM0);
                }
                return Integer.toString(target);
        }
    }
}
