package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryHistoryTest {
    @AfterEach
    void clearHistory() {
        TelemetryHistory.clear();
    }

    @Test
    void keepsOnlyTheMostRecentSamplesForEachMob() {
        assertEquals(72_000, TelemetryHistory.LIMIT);
        for (int sample = 0; sample < TelemetryHistory.LIMIT + 25; sample++) {
            TelemetryHistory.accept(packet(7, sample));
        }

        var series = TelemetryHistory.snapshot().getFirst();
        assertEquals(TelemetryHistory.LIMIT, series.samples().size());
        assertEquals(25, series.samples().getFirst().spikes());
        assertEquals(TelemetryHistory.LIMIT + 24, series.samples().getLast().spikes());
    }

    @Test
    void keepsConcurrentMobSeriesBeyondTheFormerEightMobLimit() {
        for (int id = 0; id < 12; id++) TelemetryHistory.accept(packet(id, id));

        assertEquals(12, TelemetryHistory.snapshot().size());
    }

    @Test
    void frozenSnapshotsRemainStableWhileLiveTelemetryContinues() {
        TelemetryHistory.accept(packet(7, 10));
        var frozen = TelemetryHistory.snapshot();
        TelemetryHistory.accept(packet(7, 20));
        TelemetryHistory.clear();
        assertEquals(1, frozen.getFirst().samples().size());
        assertEquals(10, frozen.getFirst().samples().getFirst().spikes());
        assertEquals(0, TelemetryHistory.snapshot().size());
    }

    @Test
    void dimensionTransitionClearsEntityIdKeyedHistoryBeforeIdsCanBeReused() {
        assertFalse(TelemetryHistory.changeDimension("minecraft:overworld"));
        TelemetryHistory.accept(packet(7, 10));
        assertFalse(TelemetryHistory.changeDimension("minecraft:overworld"));

        assertTrue(TelemetryHistory.changeDimension("minecraft:the_nether"));
        assertEquals(0, TelemetryHistory.snapshot().size());
        assertFalse(TelemetryHistory.changeDimension("minecraft:the_nether"));
    }

    @Test
    void recycledEntityIdStartsANewSeriesWhenItsUuidChanges() {
        TelemetryHistory.accept(packet(7, 10), "subject-a");
        TelemetryHistory.accept(packet(7, 11), "subject-a");
        TelemetryHistory.accept(packet(7, 20), "subject-b");

        var series = TelemetryHistory.snapshot().getFirst();
        assertEquals(1, series.samples().size());
        assertEquals(20, series.samples().getFirst().spikes());
    }

    @Test
    void unchangedSeriesReuseTheirImmutableSnapshot() {
        TelemetryHistory.accept(packet(7, 10));
        var first = TelemetryHistory.snapshot().getFirst();
        var second = TelemetryHistory.snapshot().getFirst();

        assertEquals(first, second);
        TelemetryHistory.accept(packet(7, 20));
        var updated = TelemetryHistory.snapshot().getFirst();
        assertNotEquals(first.revision(), updated.revision());
        assertEquals(20, updated.samples().getLast().spikes());
    }

    @Test
    void longHistoryDropsLargeFiringIdArraysButLatestRetainsThem() {
        var original = new BrainTelemetryPayload(7, 10, 42, 0.25f, -0.4f,
                0.1f, 0.8f, false);
        TelemetryHistory.accept(original);
        var series = TelemetryHistory.snapshot().getFirst();
        assertEquals(0, series.samples().getFirst().firingNeuronCount());
        assertEquals(0, TelemetryHistory.latest(7).firingNeuronCount());

        int[] ids = {2, 9};
        var active = new BrainTelemetryPayload(8, 11, 42, 0.25f, -0.4f,
                0.1f, 0.8f, false);
        var full = new BrainTelemetryPayload(8, 11, 0L, 0, 0, 0, 0, 1, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false,
                lab.neurolab.brain.ControlMode.ASSISTED, ids);
        TelemetryHistory.accept(full);
        assertEquals(2, TelemetryHistory.latest(8).firingNeuronCount());
        assertEquals(0, TelemetryHistory.snapshot().stream().filter(s -> s.entityId() == 8)
                .findFirst().orElseThrow().samples().getFirst().firingNeuronCount());
    }

    private static BrainTelemetryPayload packet(int entityId, int spikes) {
        return new BrainTelemetryPayload(entityId, spikes, 42, 0.25f, -0.4f,
                0.1f, 0.8f, false);
    }
}
