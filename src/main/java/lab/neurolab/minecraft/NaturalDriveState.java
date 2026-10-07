package lab.neurolab.minecraft;

import java.util.LinkedHashSet;

/** Per-subject, bounded game-embodiment drives; these are experimental proxies, not fly physiology. */
public final class NaturalDriveState {
    private static final int VISITED_CELL_CAPACITY = 128;
    private static final int PLANT_MEMORY_CAPACITY = 128;
    private static final long FLOWER_COOLDOWN_TICKS = 200;
    private static final long TREE_COOLDOWN_TICKS = 400;
    private static final long WATER_COOLDOWN_TICKS = 100;
    private static final double FLOWER_RANGE = 2.5;
    private static final double TOUCH_RANGE = 1.35;

    public record Snapshot(float reward, float pain, float satiety, float hydration, float rainStress,
                           float flowerReward, float treeReward, float waterReward) {}

    private final LinkedHashSet<Long> visitedCells = new LinkedHashSet<>();
    private final LinkedHashSet<Long> rewardedFlowers = new LinkedHashSet<>();
    private final LinkedHashSet<Long> rewardedTrees = new LinkedHashSet<>();
    private float satiety = 1.0f;
    private float hydration = 1.0f;
    private float rainStress;
    private long nextFlowerRewardTick;
    private long nextTreeRewardTick;
    private long nextWaterRewardTick;
    private long lastTick = Long.MIN_VALUE;

    public NaturalDriveState() {}

    public NaturalDriveState(float satiety, float hydration, float rainStress) {
        this.satiety = unit(satiety);
        this.hydration = unit(hydration);
        this.rainStress = unit(rainStress);
    }

    public NaturalDriveState(float satiety, float hydration, float rainStress,
                             long[] flowers, long[] trees, long[] cells) {
        this(satiety, hydration, rainStress);
        restore(rewardedFlowers, flowers, PLANT_MEMORY_CAPACITY);
        restore(rewardedTrees, trees, PLANT_MEMORY_CAPACITY);
        restore(visitedCells, cells, VISITED_CELL_CAPACITY);
    }

    public Snapshot update(long tick, boolean raining, boolean inWater, double flowerDistance,
                           long flowerKey, double treeDistance, long treeKey, long explorationCell) {
        long elapsed = lastTick == Long.MIN_VALUE ? 1 : Math.max(0, Math.min(200, tick - lastTick));
        lastTick = tick;
        satiety = Math.max(0, satiety - (float) elapsed / 48_000.0f);
        hydration = Math.max(0, hydration - (float) elapsed / 24_000.0f);
        rainStress = unit(rainStress + (raining ? elapsed / 100.0f : -elapsed / 200.0f));

        float reward = 0, flowerReward = 0, treeReward = 0, waterReward = 0;
        if (Double.isFinite(flowerDistance) && flowerDistance >= 0 && flowerDistance <= FLOWER_RANGE
                && tick >= nextFlowerRewardTick && remember(rewardedFlowers, flowerKey)) {
            double closeness = 1 - flowerDistance / FLOWER_RANGE;
            float refill = flowerDistance <= TOUCH_RANGE
                    ? Math.min(0.22f, (1 - satiety) * 0.22f) : 0;
            satiety = unit(satiety + refill);
            reward = (float) (0.06 * closeness) + refill * 0.7f;
            flowerReward = reward;
            nextFlowerRewardTick = tick + FLOWER_COOLDOWN_TICKS;
        }
        if (Double.isFinite(treeDistance) && treeDistance >= 0 && treeDistance <= TOUCH_RANGE
                && tick >= nextTreeRewardTick && remember(rewardedTrees, treeKey)) {
            reward = Math.max(reward, 0.08f);
            treeReward = 0.08f;
            nextTreeRewardTick = tick + TREE_COOLDOWN_TICKS;
        }
        if (inWater && tick >= nextWaterRewardTick) {
            float refill = Math.min(0.3f, (1 - hydration) * 0.3f);
            hydration = unit(hydration + refill);
            reward = Math.max(reward, 0.07f + refill * 0.5f);
            waterReward = 0.07f + refill * 0.5f;
            nextWaterRewardTick = tick + WATER_COOLDOWN_TICKS;
        }
        if (visitedCells.add(explorationCell)) {
            reward = Math.max(reward, 0.04f);
            if (visitedCells.size() > VISITED_CELL_CAPACITY) {
                var iterator = visitedCells.iterator();
                iterator.next();
                iterator.remove();
            }
        }

        float pain = raining ? 0.015f + rainStress * 0.055f : 0;
        if (satiety < 0.12f) pain = Math.max(pain, (0.12f - satiety) * 0.5f);
        if (hydration < 0.12f) pain = Math.max(pain, (0.12f - hydration) * 0.7f);
        return new Snapshot(unit(reward), unit(pain), satiety, hydration, rainStress,
                flowerReward, treeReward, waterReward);
    }

    public float satiety() { return satiety; }
    public float hydration() { return hydration; }
    public float rainStress() { return rainStress; }
    public long[] rewardedFlowers() { return toArray(rewardedFlowers); }
    public long[] rewardedTrees() { return toArray(rewardedTrees); }
    public long[] visitedCells() { return toArray(visitedCells); }

    public void seedExplorationCell(long cell) {
        remember(visitedCells, cell, VISITED_CELL_CAPACITY);
    }

    private static boolean remember(LinkedHashSet<Long> memory, long key) {
        return remember(memory, key, PLANT_MEMORY_CAPACITY);
    }

    private static boolean remember(LinkedHashSet<Long> memory, long key, int capacity) {
        if (!memory.add(key)) return false;
        if (memory.size() > capacity) {
            var iterator = memory.iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    private static void restore(LinkedHashSet<Long> memory, long[] keys, int capacity) {
        int start = Math.max(0, keys.length - capacity);
        for (int i = start; i < keys.length; i++) memory.add(keys[i]);
    }

    private static long[] toArray(LinkedHashSet<Long> memory) {
        return memory.stream().mapToLong(Long::longValue).toArray();
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
}
