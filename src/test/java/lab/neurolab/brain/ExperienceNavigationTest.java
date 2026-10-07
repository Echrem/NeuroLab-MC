package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ExperienceNavigationTest {
    @Test void cueOutcomeIsBoundedSpecificAndRestorable() {
        ExperienceNavigation memory = new ExperienceNavigation(new float[0]);
        assertFalse(memory.attractedTo(ExperienceNavigation.Cue.FLOWER));
        memory.observe(ExperienceNavigation.Cue.FLOWER, 0.7f, 0);
        assertTrue(memory.attractedTo(ExperienceNavigation.Cue.FLOWER));
        assertTrue(memory.preference(ExperienceNavigation.Cue.FLOWER) > 0);
        assertEquals(0, memory.preference(ExperienceNavigation.Cue.WATER));
        memory.observe(ExperienceNavigation.Cue.FIRE, 0, 1);
        assertTrue(memory.preference(ExperienceNavigation.Cue.FIRE) < 0);
        ExperienceNavigation restored = new ExperienceNavigation(memory.snapshot());
        assertEquals(memory.preference(ExperienceNavigation.Cue.FLOWER), restored.preference(ExperienceNavigation.Cue.FLOWER));
    }

    @Test void failedRouteMemoryExpiresAndIsBounded() {
        ExperienceNavigation memory = new ExperienceNavigation(new float[0]);
        memory.markBlocked(12L, 10);
        assertTrue(memory.isBlocked(12L, 100));
        assertFalse(memory.isBlocked(12L, 611));
        for (long cell = 0; cell < 200; cell++) memory.markBlocked(cell, 700);
        assertFalse(memory.isBlocked(0, 701));
        assertTrue(memory.isBlocked(199, 701));
    }

    @Test void oneSmallButSpecificFlowerRewardCanStartAttraction() {
        ExperienceNavigation memory = new ExperienceNavigation(new float[0]);
        assertFalse(memory.attractedTo(ExperienceNavigation.Cue.FLOWER));
        memory.observe(ExperienceNavigation.Cue.FLOWER, 0.025f, 0);
        assertTrue(memory.attractedTo(ExperienceNavigation.Cue.FLOWER));
    }
}
