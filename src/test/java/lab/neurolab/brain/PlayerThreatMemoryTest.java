package lab.neurolab.brain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerThreatMemoryTest {
    @Test void cueStrengthBuildsGraduallyAndResetsForAnotherAttacker() {
        UUID player = UUID.randomUUID();
        PlayerThreatMemory memory = new PlayerThreatMemory(null, 0);
        ExperienceNavigation association = new ExperienceNavigation(new float[0]);
        for (int hit = 1; hit <= 2; hit++) {
            assertEquals(hit, memory.recordHit(player));
            association.observe(ExperienceNavigation.Cue.PLAYER, 0, 0.55f);
            assertFalse(memory.hasLearnedThreat(association.preference(ExperienceNavigation.Cue.PLAYER)));
        }
        assertEquals(3, memory.recordHit(player));
        association.observe(ExperienceNavigation.Cue.PLAYER, 0, 0.55f);
        assertTrue(memory.hasLearnedThreat(association.preference(ExperienceNavigation.Cue.PLAYER)));
        UUID other = UUID.randomUUID();
        assertEquals(1, memory.recordHit(other));
        association.clear(ExperienceNavigation.Cue.PLAYER);
        assertFalse(memory.hasLearnedThreat(association.preference(ExperienceNavigation.Cue.PLAYER)));
        assertEquals(other, memory.aggressor());
    }
}
