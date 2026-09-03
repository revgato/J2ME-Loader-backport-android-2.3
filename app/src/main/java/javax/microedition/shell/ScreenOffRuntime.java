/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package javax.microedition.shell;

/** Coordinates screen-off lifecycle decisions with the platform power locks. */
final class ScreenOffRuntime {
    interface Locks {
        void acquire();

        void release();
    }

    private final boolean keepRunningWhenScreenOff;
    private final Locks locks;
    private boolean screenOff;
    private boolean locksHeld;

    ScreenOffRuntime(boolean keepRunningWhenScreenOff, Locks locks) {
        this.keepRunningWhenScreenOff = keepRunningWhenScreenOff;
        this.locks = locks;
    }

    void onScreenOff() {
        screenOff = true;
        if (keepRunningWhenScreenOff) {
            acquireLocks();
        }
    }

    void onScreenOn() {
        screenOff = false;
        releaseLocks();
    }

    /**
     * Returns whether the MIDlet should receive its normal Activity pause callback.
     *
     * @param screenOn current screen state, used to cover a broadcast/lifecycle race
     */
    boolean shouldPauseOnActivityPause(boolean screenOn) {
        if (keepRunningWhenScreenOff && (screenOff || !screenOn)) {
            screenOff = true;
            acquireLocks();
            return false;
        }
        return true;
    }

    void close() {
        screenOff = false;
        releaseLocks();
    }

    private void acquireLocks() {
        if (!locksHeld) {
            locks.acquire();
            locksHeld = true;
        }
    }

    private void releaseLocks() {
        if (locksHeld) {
            locks.release();
            locksHeld = false;
        }
    }
}
