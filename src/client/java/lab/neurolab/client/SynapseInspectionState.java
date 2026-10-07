package lab.neurolab.client;

import lab.neurolab.minecraft.SynapseInspectionPayload;

/** The response belongs to a single subject/neuron selection, never to a global anonymous panel. */
public final class SynapseInspectionState {
    private static volatile SynapseInspectionPayload latest;
    private static volatile long receivedAt;
    private SynapseInspectionState() {}
    public static void accept(SynapseInspectionPayload payload) {
        latest = payload;
        receivedAt = System.nanoTime();
    }
    public static void clear() { latest = null; receivedAt = 0; }
    public static SynapseInspectionPayload get(int entityId, int neuronId) {
        SynapseInspectionPayload result = latest;
        return result != null && System.nanoTime() - receivedAt < 2_000_000_000L
                && result.entityId() == entityId && result.neuronId() == neuronId ? result : null;
    }
}
