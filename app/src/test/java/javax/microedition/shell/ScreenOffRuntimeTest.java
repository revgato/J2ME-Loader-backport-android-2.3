/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package javax.microedition.shell;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenOffRuntimeTest {
    @Test
    public void disabledModeStillPausesWhenScreenTurnsOff() {
        FakeLocks locks = new FakeLocks();
        ScreenOffRuntime runtime = new ScreenOffRuntime(false, locks);

        runtime.onScreenOff();

        assertTrue(runtime.shouldPauseOnActivityPause(false));
        assertEquals(0, locks.acquireCount);
    }

    @Test
    public void enabledModeKeepsMidletRunningAndAcquiresLocks() {
        FakeLocks locks = new FakeLocks();
        ScreenOffRuntime runtime = new ScreenOffRuntime(true, locks);

        runtime.onScreenOff();

        assertFalse(runtime.shouldPauseOnActivityPause(false));
        assertEquals(1, locks.acquireCount);
    }

    @Test
    public void pauseRaceStillKeepsRunningWhenScreenIsAlreadyOff() {
        FakeLocks locks = new FakeLocks();
        ScreenOffRuntime runtime = new ScreenOffRuntime(true, locks);

        assertFalse(runtime.shouldPauseOnActivityPause(false));

        assertEquals(1, locks.acquireCount);
    }

    @Test
    public void screenOnReleasesLocksAndRestoresNormalPauseBehavior() {
        FakeLocks locks = new FakeLocks();
        ScreenOffRuntime runtime = new ScreenOffRuntime(true, locks);
        runtime.onScreenOff();

        runtime.onScreenOn();

        assertTrue(runtime.shouldPauseOnActivityPause(true));
        assertEquals(1, locks.releaseCount);
    }

    @Test
    public void closeReleasesLocksAfterScreenOff() {
        FakeLocks locks = new FakeLocks();
        ScreenOffRuntime runtime = new ScreenOffRuntime(true, locks);
        runtime.onScreenOff();

        runtime.close();
        runtime.close();

        assertEquals(1, locks.releaseCount);
    }

    private static final class FakeLocks implements ScreenOffRuntime.Locks {
        int acquireCount;
        int releaseCount;

        @Override
        public void acquire() {
            acquireCount++;
        }

        @Override
        public void release() {
            releaseCount++;
        }
    }
}
