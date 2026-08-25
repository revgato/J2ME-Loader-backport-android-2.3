package ru.playsoftware.j2meloader.legacy;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyKeyMappingTest {
    @Test
    public void roundTripsPhysicalAndCanvasArrays() {
        LegacyKeyMapping mapping = LegacyKeyMapping.fromArrays(
                new int[] {4, 65, 66}, new int[] {0, -6, -5});

        assertArrayEquals(new int[] {4, 65, 66}, mapping.physicalKeys());
        assertArrayEquals(new int[] {0, -6, -5}, mapping.canvasKeys());
        assertEquals(65, mapping.physicalKeyFor(-6, -1));
    }

    @Test
    public void assigningKeyRemovesPreviousPhysicalAndCanvasDuplicates() {
        LegacyKeyMapping mapping = LegacyKeyMapping.fromArrays(
                new int[] {4, 23, 66}, new int[] {0, -5, -6});

        mapping.assign(4, -6);

        assertArrayEquals(new int[] {4, 23}, mapping.physicalKeys());
        assertArrayEquals(new int[] {-6, -5}, mapping.canvasKeys());
    }

    @Test
    public void resetCopiesDefaultsWithoutSharingState() {
        LegacyKeyMapping defaults = LegacyKeyMapping.fromArrays(
                new int[] {4, 66}, new int[] {0, -5});
        LegacyKeyMapping mapping = defaults.copy();

        mapping.assign(23, -5);
        mapping.reset(defaults);

        assertEquals(defaults, mapping);
        assertFalse(mapping == defaults);
        assertTrue(mapping.hasCanvasKey(0));
    }

    @Test
    public void detectsMissingMenuMapping() {
        LegacyKeyMapping mapping = LegacyKeyMapping.fromArrays(
                new int[] {23}, new int[] {-5});

        assertFalse(mapping.hasCanvasKey(0));
        assertEquals(-1, mapping.physicalKeyFor(0, -1));
    }
}
