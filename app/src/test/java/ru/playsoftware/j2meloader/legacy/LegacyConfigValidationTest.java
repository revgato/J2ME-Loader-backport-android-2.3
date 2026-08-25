package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import static org.junit.Assert.fail;

public class LegacyConfigValidationTest {
    @Test
    public void acceptsLegacySizedConfig() {
        LegacyConfigValidation.validateScreen(240, 320);
        LegacyConfigValidation.validateScale(100);
        LegacyConfigValidation.validateFps(0);
        LegacyConfigValidation.validateFont(26);
        LegacyConfigValidation.validateSystemProperties("microedition.platform: Nokia6233\n");
    }

    @Test
    public void rejectsUnsafeScreenScaleFpsAndFontValues() {
        rejectsScreen(63, 320);
        rejectsScreen(240, 1025);
        rejectsScale(24);
        rejectsScale(401);
        rejectsFps(-1);
        rejectsFps(121);
        rejectsFont(-1);
        rejectsFont(97);
    }

    @Test
    public void rejectsMalformedOrOversizedProperties() {
        try {
            LegacyConfigValidation.validateSystemProperties("not a property");
            fail("malformed property should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            LegacyConfigValidation.validateSystemProperties(repeat('x',
                    LegacyConfigValidation.MAX_SYSTEM_PROPERTIES + 1));
            fail("oversized properties should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void rejectsScreen(int width, int height) {
        try {
            LegacyConfigValidation.validateScreen(width, height);
            fail("screen should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void rejectsScale(int value) {
        try {
            LegacyConfigValidation.validateScale(value);
            fail("scale should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void rejectsFps(int value) {
        try {
            LegacyConfigValidation.validateFps(value);
            fail("fps should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void rejectsFont(int value) {
        try {
            LegacyConfigValidation.validateFont(value);
            fail("font should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(c);
        return result.toString();
    }
}
