package lab.neurolab.brain;

import java.util.UUID;

/** Per-subject, identity-specific record of repeated damaging player encounters. */
public final class PlayerThreatMemory {
    public static final float AVOIDANCE_CUE_THRESHOLD = 0.12f;
    private UUID aggressor;
    private int damagingHits;

    public PlayerThreatMemory(UUID aggressor, int damagingHits) {
        this.aggressor = aggressor;
        this.damagingHits = Math.max(0, damagingHits);
    }

    public int recordHit(UUID player) {
        if (player == null) return damagingHits;
        if (!player.equals(aggressor)) {
            aggressor = player;
            damagingHits = 0;
        }
        damagingHits = Math.min(100_000, damagingHits + 1);
        return damagingHits;
    }

    public UUID aggressor() { return aggressor; }
    public int damagingHits() { return damagingHits; }
    public boolean hasLearnedThreat(float cuePreference) {
        return aggressor != null && cuePreference < -AVOIDANCE_CUE_THRESHOLD;
    }
}
