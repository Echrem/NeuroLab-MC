package lab.neurolab.brain;

import java.util.Arrays;

/** A reusable sparse set: each neuron is integrated at most once per substep. */
final class NeuronQueue {
    private final boolean[] present;
    private int[] ids;
    private int size;

    NeuronQueue(int neurons) {
        if (neurons < 1) throw new IllegalArgumentException("A queue needs at least one neuron");
        present = new boolean[neurons];
        ids = new int[Math.min(neurons, Math.max(256, neurons / 32))];
    }

    void add(int id) {
        if (present[id]) return;
        if (size == ids.length) ids = Arrays.copyOf(ids, Math.min(present.length, ids.length * 2));
        ids[size++] = id;
        present[id] = true;
    }

    int size() { return size; }
    int get(int index) { return ids[index]; }

    void clear() {
        for (int i = 0; i < size; i++) present[ids[i]] = false;
        size = 0;
    }
}
