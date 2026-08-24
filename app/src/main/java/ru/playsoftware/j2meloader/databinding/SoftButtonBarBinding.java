/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Platform-only replacement for the generated view binding used by Screen. */
public final class SoftButtonBarBinding {
    public final LinearLayout rootLayout;
    public final Button leftButton;
    public final Button middleButton;
    public final Button rightButton;

    private SoftButtonBarBinding(LinearLayout root, Button left, Button middle, Button right) {
        rootLayout = root;
        leftButton = left;
        middleButton = middle;
        rightButton = right;
    }

    public static SoftButtonBarBinding inflate(LayoutInflater inflater, ViewGroup parent,
                                               boolean attachToParent) {
        LinearLayout root = new LinearLayout(inflater.getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(4, 0, 4, 0);
        Button left = button(inflater);
        Button middle = button(inflater);
        Button right = button(inflater);
        root.addView(left, weightParams());
        root.addView(middle, weightParams());
        root.addView(right, weightParams());
        if (attachToParent) {
            parent.addView(root, new ViewGroup.LayoutParams(-1, -2));
        }
        return new SoftButtonBarBinding(root, left, middle, right);
    }

    private static Button button(LayoutInflater inflater) {
        Button button = new Button(inflater.getContext());
        button.setSingleLine(true);
        return button;
    }

    private static LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }
}
