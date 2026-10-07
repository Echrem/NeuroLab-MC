package lab.neurolab.brain;

import java.util.List;

/** The user-facing contract for what is prewired, assisted, or experience-dependent. */
public final class BehaviorCatalog {
    public enum Layer { AUTOMATIC, ASSISTED, LEARNED }
    public record Entry(Layer layer, String name, String mechanism) {}
    private static final List<Entry> ENTRIES = List.of(
            new Entry(Layer.AUTOMATIC, "Pain / injury", "Damage, fire, lava and hazardous contact generate aversive input."),
            new Entry(Layer.AUTOMATIC, "Rain shelter", "Neuro Fly seeks cover when exposed; this is a configured reflex."),
            new Entry(Layer.AUTOMATIC, "Hunger / thirst", "Non-lethal need meters bias cues; neither need damages nor kills the mob."),
            new Entry(Layer.AUTOMATIC, "Flight stability", "Terrain clearance and a height ceiling prevent runaway ascent."),
            new Entry(Layer.AUTOMATIC, "Looming / sound", "Immediate motion and sound can trigger reflexive steering; no identity memory."),
            new Entry(Layer.ASSISTED, "Collision steering", "Local rays temper neural forward and turning commands."),
            new Entry(Layer.ASSISTED, "Body movement", "Decoded neural motor output is bounded by each mob's anatomy."),
            new Entry(Layer.ASSISTED, "Perching / landing", "Neuro Fly can land and cling using game-world collision checks."),
            new Entry(Layer.ASSISTED, "Visual contrast", "Left and right brightness cues bias steering in assisted mode."),
            new Entry(Layer.LEARNED, "Failed-cell memory", "Blocked movement is remembered briefly to bias the next turn."),
            new Entry(Layer.LEARNED, "Synaptic weights", "Reward follows simulated pre/post spike pairs on real directed edges."),
            new Entry(Layer.LEARNED, "Flower attraction", "Flower-seeking starts only after a positive flower encounter."),
            new Entry(Layer.LEARNED, "Zombie pursuit", "An attached zombie pursues villagers only after rewarded contact."),
            new Entry(Layer.LEARNED, "Villager aversion", "An attached villager avoids zombies after contact or injury."),
            new Entry(Layer.LEARNED, "Attacker recognition", "Damage from one player gradually builds an identity-specific aversive cue."),
            new Entry(Layer.LEARNED, "Block outcomes", "Repeated block contact pairs pain with avoidance; observed recovery pairs support blocks with reward."),
            new Entry(Layer.LEARNED, "Cue preferences", "Flower, tree, water, fire, social and player cues retain bounded scores."));

    private BehaviorCatalog() {}
    public static List<Entry> entries() { return ENTRIES; }
}
