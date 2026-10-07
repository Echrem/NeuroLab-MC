package lab.neurolab.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SynapticPlasticityTest {
    @Test void replacingAnImprintClearsPreviousEligibilityTraces() {
        SynapticPlasticity learning = new SynapticPlasticity(new long[0], 100);
        learning.record(17, 4);
        learning.replace(new long[0], 100);
        learning.applyFeedback(1, 0, 5);
        assertEquals(1, learning.weightScale(17));
        assertEquals(0, learning.snapshot().length);
        learning.record(23, 6);
        learning.applyFeedback(1, 0, 6);
        assertTrue(learning.weightScale(23) > 1);
    }
    @Test
    void rewardAndPainChangeOnlyRecentlyActiveDirectedEdges() {
        SynapticPlasticity learning = new SynapticPlasticity(new long[0], 100);
        learning.record(17, 4);
        learning.record(23, 4);
        learning.applyFeedback(1, 0, 4);

        assertTrue(learning.weightScale(17) > 1);
        assertTrue(learning.weightScale(23) > 1);
        assertEquals(1, learning.weightScale(18));
        assertEquals(1, learning.revision());

        learning.record(17, 20);
        learning.applyFeedback(1, 0, 20);
        for (int step = 21; step <= 25; step++) {
            learning.record(17, step);
            learning.applyFeedback(0, 1, step);
        }
        assertTrue(learning.weightScale(17) < 1);
        assertTrue(learning.weightScale(23) > 1);
    }

    @Test void duplicatePairsInOneTickCannotMultiplyTheSameReward() {
        SynapticPlasticity once = new SynapticPlasticity(new long[0], 100);
        SynapticPlasticity repeated = new SynapticPlasticity(new long[0], 100);
        once.record(17, 3);
        for (int i = 0; i < 100; i++) repeated.record(17, 3);
        once.applyFeedback(1, 0, 3);
        repeated.applyFeedback(1, 0, 3);
        assertEquals(once.weightScale(17), repeated.weightScale(17));
        repeated.applyFeedback(1, 0, 4);
        assertEquals(once.weightScale(17), repeated.weightScale(17));
    }

    @Test void expiredPairsDoNotLearnAndUnrelatedEdgesRemainUnchanged() {
        SynapticPlasticity learning = new SynapticPlasticity(new long[0], 100);
        learning.record(17, 1);
        learning.applyFeedback(1, 0, 20);
        assertEquals(1, learning.weightScale(17));
        assertEquals(1, learning.weightScale(18));
    }

    @Test
    void recentlyActiveEdgesRemainEligibleForRewardArrivingAfterTheSpike() {
        SynapticPlasticity learning = new SynapticPlasticity(new long[0], 100);
        learning.record(17, 4);
        learning.applyFeedback(0, 0, 4);
        assertEquals(1, learning.weightScale(17));

        learning.applyFeedback(1, 0, 5);
        assertTrue(learning.weightScale(17) > 1);
    }

    @Test
    void learnedEdgeWeightsAreBoundedAndCanBeRestored() {
        SynapticPlasticity learning = new SynapticPlasticity(new long[0], 4);
        for (int step = 1; step <= 20_000; step++) {
            learning.record(2, step);
            learning.applyFeedback(1, 0, step);
        }
        assertTrue(learning.weightScale(2) <= 1.25);

        SynapticPlasticity restored = new SynapticPlasticity(learning.snapshot(), 4);
        assertEquals(learning.weightScale(2), restored.weightScale(2), 0.00001);
        assertEquals(1, restored.weightScale(3));
    }

    @Test
    void invalidPersistedEdgeIndexesAreIgnored() {
        long invalid = ((long) 99 << 32) | (Float.floatToIntBits(0.1f) & 0xffffffffL);
        SynapticPlasticity learning = new SynapticPlasticity(new long[]{invalid}, 4);
        assertEquals(0, learning.snapshot().length);
    }
}
