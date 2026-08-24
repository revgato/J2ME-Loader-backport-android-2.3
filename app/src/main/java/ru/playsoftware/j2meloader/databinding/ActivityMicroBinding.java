/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import javax.microedition.lcdui.overlay.OverlayView;

/** Hand-written API 10 binding; generated AndroidX view binding is intentionally disabled. */
public final class ActivityMicroBinding {
    private final FrameLayout root;
    public final TextView toolbar;
    public final FrameLayout displayableContainer;
    public final OverlayView overlayView;

    private ActivityMicroBinding(FrameLayout root, TextView toolbar, FrameLayout displayableContainer,
                                 OverlayView overlayView) {
        this.root = root;
        this.toolbar = toolbar;
        this.displayableContainer = displayableContainer;
        this.overlayView = overlayView;
    }

    public static ActivityMicroBinding inflate(LayoutInflater inflater) {
        FrameLayout root = new FrameLayout(inflater.getContext());
        LinearLayout display = new LinearLayout(inflater.getContext());
        display.setOrientation(LinearLayout.VERTICAL);
        TextView toolbar = new TextView(inflater.getContext());
        toolbar.setTextColor(0xffffffff);
        toolbar.setTextSize(18);
        toolbar.setPadding(12, 8, 12, 8);
        toolbar.setVisibility(View.GONE);
        display.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout container = new FrameLayout(inflater.getContext());
        display.addView(container, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(display, new FrameLayout.LayoutParams(-1, -1));
        OverlayView overlay = new OverlayView(inflater.getContext());
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        return new ActivityMicroBinding(root, toolbar, container, overlay);
    }

    public View getRoot() {
        return root;
    }
}
