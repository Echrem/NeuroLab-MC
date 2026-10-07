package lab.neurolab.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.CommentedConfig;

final class NeuroFlyConfigTest {
    @Test
    void environmentConfigLoadsSafeTunableDefaults() {
        NeuroFlyConfig.SPEC.acceptConfig(CommentedConfig.inMemory());
        assertEquals(NeuroFlyConfig.LightMode.BALANCED, NeuroFlyConfig.LIGHT_MODE.get());
        assertEquals(0.9, NeuroFlyConfig.LIGHT_STEERING.get());
        assertEquals(0.30, NeuroFlyConfig.CRUISE_THROTTLE.get());
        assertEquals(2.0, NeuroFlyConfig.CRUISE_ALTITUDE.get());
        assertEquals(5.0, NeuroFlyConfig.ALTITUDE_CEILING.get());
        assertEquals(8, NeuroFlyConfig.FLOWER_RADIUS.get());
        assertEquals(8, NeuroFlyConfig.HOST_RADIUS.get());
        assertEquals(120, NeuroFlyConfig.PERCH_TICKS.get());
        assertEquals(40, NeuroFlyConfig.RAIN_SCAN_INTERVAL.get());
        assertTrue(NeuroFlyConfig.SEEK_SHELTER_IN_RAIN.get());
        assertTrue(NeuroFlyConfig.PERCH_ON_ANIMALS_AND_VILLAGERS.get());
    }

    @Test
    void oldDefaultMigratesToTheExpandedSampleButCustomValuesAreKept() {
        assertEquals(65_536, NeuroFlyConfig.migrateSampleLimit(256, 0));
        assertEquals(65_536, NeuroFlyConfig.migrateSampleLimit(25_600, 1));
        assertEquals(256, NeuroFlyConfig.migrateSampleLimit(256, 1));
        assertEquals(512, NeuroFlyConfig.migrateSampleLimit(512, 0));
        assertEquals(0, NeuroFlyConfig.migrateSampleLimit(0, 0));
    }
}
