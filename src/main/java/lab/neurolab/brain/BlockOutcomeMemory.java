package lab.neurolab.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small per-subject association between encountered block types and measured outcomes. */
public final class BlockOutcomeMemory {
    private static final int CAPACITY = 32;
    private static final float LEARNING_RATE = 0.12f;
    private final LinkedHashMap<String, Float> values = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
            return size() > CAPACITY;
        }
    };

    public BlockOutcomeMemory(CompoundTag saved) {
        for (String key : saved.getAllKeys()) {
            float value = saved.getFloat(key);
            if (ResourceLocation.tryParse(key) != null && Float.isFinite(value))
                values.put(key, Math.max(-1, Math.min(1, value)));
        }
    }

    public void observe(ResourceLocation block, float reward, float pain) {
        if (block == null) return;
        float outcome = Math.max(-1, Math.min(1, reward - pain));
        if (Math.abs(outcome) < 0.01f) return;
        String key = block.toString();
        values.put(key, Math.max(-1, Math.min(1,
                values.getOrDefault(key, 0f) * 0.92f + outcome * LEARNING_RATE)));
    }

    public float preference(ResourceLocation block) {
        return block == null ? 0 : values.getOrDefault(block.toString(), 0f);
    }

    public boolean avoids(ResourceLocation block) { return preference(block) < -0.12f; }
    public int size() { return values.size(); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        values.forEach(tag::putFloat);
        return tag;
    }

    public Map.Entry<String, Float> strongest() {
        return values.entrySet().stream().max(Map.Entry.comparingByValue(
                java.util.Comparator.comparingDouble(Math::abs))).map(Map.Entry::copyOf).orElse(null);
    }
}
