package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Is14shKeyProfileTest {
    @Test
    public void matchesSharpModelAndMapsPhysicalKeys() {
        Is14shKeyProfile profile = Is14shKeyProfile.forDevice("IS14SH", "is14sh");
        assertEquals("0", profile.getKeyNames().get(Is14shKeyProfile.KEYCODE_0));
        assertEquals("*", profile.getKeyNames().get(Is14shKeyProfile.KEYCODE_STAR));
        assertEquals("FIRE", profile.getKeyNames().get(Is14shKeyProfile.KEYCODE_DPAD_CENTER));
        assertEquals("MAIL", profile.getKeyNames().get(Is14shKeyProfile.KEYCODE_ENVELOPE));
    }

    @Test
    public void doesNotApplyToOtherDevices() {
        assertNull(Is14shKeyProfile.forDevice("Nexus One", "passion"));
    }
}
