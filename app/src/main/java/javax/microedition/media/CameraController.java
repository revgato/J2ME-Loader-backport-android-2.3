/* Copyright 2026 J2ME-Loader contributors. Licensed under the Apache License, Version 2.0. */
package javax.microedition.media;

/** Camera is intentionally absent from the API 10 MVP. */
public final class CameraController {
    public void setUp(Object ignored) {
        throw LegacyUnsupported.camera();
    }

    public byte[] getSnapshot() {
        throw LegacyUnsupported.camera();
    }
}
