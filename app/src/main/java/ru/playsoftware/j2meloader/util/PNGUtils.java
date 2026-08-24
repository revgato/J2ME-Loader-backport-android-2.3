/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;

/** Platform PNG decoding for API 10; malformed palette repair is intentionally omitted. */
public final class PNGUtils {
    private PNGUtils() { }

    public static Bitmap getFixedBitmap(InputStream stream) {
        return BitmapFactory.decodeStream(stream);
    }

    public static Bitmap getFixedBitmap(byte[] imageData, int imageOffset, int imageLength) {
        return BitmapFactory.decodeByteArray(imageData, imageOffset, imageLength);
    }
}
