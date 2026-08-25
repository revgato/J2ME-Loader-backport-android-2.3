package javax.microedition.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContextHolderTest {
    @Test
    public void skipsHasVibratorProbeOnApi10() {
        assertFalse(ContextHolder.shouldQueryVibratorHardware(10));
    }

    @Test
    public void probesHasVibratorOnApi11AndLater() {
        assertTrue(ContextHolder.shouldQueryVibratorHardware(11));
        assertTrue(ContextHolder.shouldQueryVibratorHardware(34));
    }
}
