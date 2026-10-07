package lab.neurolab.client;

import lab.neurolab.minecraft.BrainRosterPayload;
import lab.neurolab.brain.StructuralPathway;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Latest server-authoritative list of attached mobs in the current dimension. */
public final class TelemetryRoster {
    private static final AtomicReference<List<BrainRosterPayload.Entry>> ENTRIES = new AtomicReference<>(List.of());
    private static final AtomicReference<List<StructuralPathway.Step>> PATHWAY = new AtomicReference<>(List.of());
    private TelemetryRoster() {}

    public static void replace(BrainRosterPayload payload) {
        ENTRIES.set(List.copyOf(payload.entries()));
        PATHWAY.set(List.copyOf(payload.pathway()));
    }

    public static List<BrainRosterPayload.Entry> snapshot() { return ENTRIES.get(); }
    public static List<StructuralPathway.Step> pathway() { return PATHWAY.get(); }
    public static void clear() { ENTRIES.set(List.of()); PATHWAY.set(List.of()); }
}
