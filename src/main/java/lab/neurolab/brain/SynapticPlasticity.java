package lab.neurolab.brain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded reward-modulated eligibility traces keyed by directed CSR edge index. */
final class SynapticPlasticity {
    private static final int TRACE_CAPACITY = 4096;
    private static final int LEARNED_EDGE_CAPACITY = 8192;
    private static final int ELIGIBILITY_WINDOW = 12;
    private static final float LEARNING_RATE = 0.0001f;
    private static final float MAX_WEIGHT_CHANGE = 0.25f;

    private final LinkedHashMap<Integer, Trace> eligibility = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Trace> eldest) {
            return size() > TRACE_CAPACITY;
        }
    };
    private volatile int revision;
    private volatile Map<Integer, Float> weights = Map.of();
    private final LinkedHashMap<Integer, Float> learned = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Float> eldest) {
            return size() > LEARNED_EDGE_CAPACITY;
        }
    };

    SynapticPlasticity(long[] savedState, int edgeCount) {
        restore(savedState, edgeCount);
    }

    void replace(long[] savedState, int edgeCount) {
        learned.clear();
        eligibility.clear();
        restore(savedState, edgeCount);
        revision++;
    }

    private void restore(long[] savedState, int edgeCount) {
        for (long entry : savedState) {
            int edge = (int) (entry >>> 32);
            float change = Float.intBitsToFloat((int) entry);
            if (edge >= 0 && edge < edgeCount && Float.isFinite(change)
                    && Math.abs(change) <= MAX_WEIGHT_CHANGE && change != 0) learned.put(edge, change);
        }
        weights = Map.copyOf(learned);
    }

    void record(int directedEdge, long step) {
        Trace trace = eligibility.get(directedEdge);
        if (trace == null || step - trace.step > ELIGIBILITY_WINDOW)
            eligibility.put(directedEdge, new Trace(step, 0.25f));
        else if (trace.step != step) {
            trace.strength = Math.min(1f, trace.strength + 0.25f);
            trace.step = step;
        }
    }

    void applyFeedback(float reward, float pain, long step) {
        double feedback = Math.max(-1, Math.min(1, (double) reward - pain));
        if (Math.abs(feedback) < 0.001) return;
        boolean changed = false;
        for (var iterator = eligibility.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Integer, Trace> entry = iterator.next();
            long age = step - entry.getValue().step;
            if (age < 0 || age > ELIGIBILITY_WINDOW) { iterator.remove(); continue; }
            int edge = entry.getKey();
            float before = learned.getOrDefault(edge, 0f);
            float after = (float) Math.max(-MAX_WEIGHT_CHANGE,
                    Math.min(MAX_WEIGHT_CHANGE, before + feedback * LEARNING_RATE
                            * entry.getValue().strength * (1.0 - age / (double) (ELIGIBILITY_WINDOW + 1))));
            if (after != before) {
                learned.put(edge, after);
                changed = true;
            }
            iterator.remove(); // one reward event cannot train the same pairing repeatedly
        }
        if (changed) {
            weights = Map.copyOf(learned);
            revision++;
        }
    }

    double weightScale(int directedEdge) {
        Map<Integer, Float> activeWeights = weights;
        return activeWeights.isEmpty() ? 1 : 1 + activeWeights.getOrDefault(directedEdge, 0f);
    }

    int revision() { return revision; }
    int learnedSynapseCount() { return weights.size(); }

    long[] snapshot() {
        Map<Integer, Float> stable = weights;
        long[] state = new long[stable.size()];
        int index = 0;
        for (Map.Entry<Integer, Float> entry : stable.entrySet()) {
            state[index++] = ((long) entry.getKey() << 32) | (Float.floatToIntBits(entry.getValue()) & 0xffffffffL);
        }
        return state;
    }

    private static final class Trace {
        long step;
        float strength;
        Trace(long step, float strength) { this.step = step; this.strength = strength; }
    }
}
