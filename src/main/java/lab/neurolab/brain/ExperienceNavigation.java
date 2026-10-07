package lab.neurolab.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded game-world cue associations and recently failed route cells, separate from the connectome. */
public final class ExperienceNavigation {
    public enum Cue { FLOWER, TREE, WATER, FIRE, ZOMBIE, VILLAGER, PLAYER }
    private static final int BLOCKED_CAPACITY = 128;
    private static final long BLOCKED_TTL_TICKS = 600;
    private final float[] valence = new float[Cue.values().length];
    private final LinkedHashMap<Long, Long> blocked = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > BLOCKED_CAPACITY;
        }
    };

    public ExperienceNavigation(float[] saved) {
        for (int i = 0; i < Math.min(saved.length, valence.length); i++)
            if (Float.isFinite(saved[i])) valence[i] = Math.max(-1, Math.min(1, saved[i]));
    }

    /** A cue is reinforced only on actual proximity/contact, not from unrelated exploration rewards. */
    public void observe(Cue cue, float reward, float pain) {
        float outcome = Math.max(-1, Math.min(1, reward - pain));
        if (Math.abs(outcome) < 0.01f) return;
        int index = cue.ordinal();
        valence[index] = Math.max(-1, Math.min(1, valence[index] * 0.92f + outcome * 0.08f));
    }

    public float preference(Cue cue) { return valence[cue.ordinal()]; }
    public void clear(Cue cue) { valence[cue.ordinal()] = 0; }
    public boolean attractedTo(Cue cue) { return preference(cue) > 0.001f; }
    public boolean avoids(Cue cue, float threshold) { return preference(cue) < -threshold; }
    public float[] snapshot() { return valence.clone(); }
    public int rememberedBlockedCells() { return blocked.size(); }

    public void markBlocked(long cell, long gameTick) { blocked.put(cell, gameTick); }
    public boolean isBlocked(long cell, long gameTick) {
        Long recorded = blocked.get(cell);
        if (recorded == null) return false;
        if (gameTick - recorded <= BLOCKED_TTL_TICKS && gameTick >= recorded) return true;
        blocked.remove(cell);
        return false;
    }
}
