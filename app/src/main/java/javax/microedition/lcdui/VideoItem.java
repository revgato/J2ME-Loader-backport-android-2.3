/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.lcdui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import javax.microedition.media.CameraController;
import javax.microedition.util.ContextHolder;

/** Placeholder which reports the deliberately unsupported camera capability safely. */
public class VideoItem extends Item {
    private TextView view;
    private final CameraController controller;

    public VideoItem(CameraController controller) {
        this.controller = controller;
    }

    @Override
    protected View getItemContentView() {
        if (view == null) {
            Context context = ContextHolder.getAppContext();
            view = new TextView(context);
            view.setText("Camera unsupported on Android 2.3 legacy build");
        }
        return view;
    }

    @Override
    protected void clearItemContentView() {
        view = null;
    }
}
