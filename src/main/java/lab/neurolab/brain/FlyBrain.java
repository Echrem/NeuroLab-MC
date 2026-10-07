package lab.neurolab.brain;

import java.util.HashMap;
import java.util.BitSet;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Sparse event-driven leaky integrate-and-fire simulation over an immutable adult-fly connectome. */
public final class FlyBrain {
    public static final int MAX_FIRING_NEURON_IDS = 65_536;
    public record Drive(float light, float leftEye, float rightEye, float looming, float tactile, float odor,
                        float taste, float pain, float reward, float sound) {
        public Drive(float light, float leftEye, float rightEye, float looming, float tactile, float odor,
                     float taste) {
            this(light, leftEye, rightEye, looming, tactile, odor, taste, 0, 0, 0);
        }
        public Drive(float light, float leftEye, float rightEye, float looming, float tactile, float odor,
                     float taste, float pain, float reward) {
            this(light, leftEye, rightEye, looming, tactile, odor, taste, pain, reward, 0);
        }
        public Drive(float light, float looming, float tactile, float odor, float taste) {
            this(light, light, light, looming, tactile, odor, taste);
        }
        public Drive {
            light = clamp(light); leftEye = clamp(leftEye); rightEye = clamp(rightEye);
            looming = clamp(looming); tactile = clamp(tactile); odor = clamp(odor); taste = clamp(taste);
            pain = clamp(pain); reward = clamp(reward);
            sound = clamp(sound);
        }
        private static float clamp(float v) { return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0; }
    }
    public record Snapshot(long tick, long spikes, long synapticEvents, int activeNeurons, double forward, double turn, double lift,
                           boolean escape, Map<String, Double> populations, int[] firingNeuronIds) {
        public Snapshot(long tick, long spikes, int activeNeurons, double forward, double turn, double lift,
                        boolean escape, Map<String, Double> populations) {
            this(tick, spikes, 0, activeNeurons, forward, turn, lift, escape, populations, new int[0]);
        }

        public Snapshot(long tick, long spikes, long synapticEvents, int activeNeurons, double forward,
                        double turn, double lift, boolean escape, Map<String, Double> populations) {
            this(tick, spikes, synapticEvents, activeNeurons, forward, turn, lift, escape, populations, new int[0]);
        }

        public Snapshot { firingNeuronIds = java.util.Arrays.copyOf(firingNeuronIds, firingNeuronIds.length); }
        @Override public int[] firingNeuronIds() {
            return java.util.Arrays.copyOf(firingNeuronIds, firingNeuronIds.length);
        }
        public int[] firingNeuronIds(int limit) {
            return java.util.Arrays.copyOf(firingNeuronIds,
                    Math.min(Math.max(0, limit), firingNeuronIds.length));
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot s)) return false;
            return tick == s.tick && spikes == s.spikes && synapticEvents == s.synapticEvents
                    && activeNeurons == s.activeNeurons && Double.compare(forward, s.forward) == 0
                    && Double.compare(turn, s.turn) == 0 && Double.compare(lift, s.lift) == 0
                    && escape == s.escape && populations.equals(s.populations)
                    && java.util.Arrays.equals(firingNeuronIds, s.firingNeuronIds);
        }

        @Override public int hashCode() {
            return 31 * java.util.Objects.hash(tick, spikes, synapticEvents, activeNeurons,
                    forward, turn, lift, escape, populations) + java.util.Arrays.hashCode(firingNeuronIds);
        }
    }

    private static final double DT_MS = 0.5, TAU_MS = 20, SYNAPSE_TAU_MS = 5;
    private static final double REST_MV = -52, THRESHOLD_MV = -45, RESET_MV = -52;
    private static final double SYNAPSE_SCALE_MV = 0.0008;
    private final ConnectomeData graph;
    private final SynapticPlasticity plasticity;
    private final ConnectomeData.IncomingIndex incoming;
    private final long[] lastSpikeSubstep;
    private final AtomicReference<long[]> pendingMindCopy = new AtomicReference<>();
    private final double[] voltage, current;
    private final byte[] refractory;
    private final BitSet sampledFiringNeurons;
    private NeuronQueue active, next;
    private final int[] sensory, leftEye, rightEye;
    private final int[] olfactory, gustatory, tactile;
    private final String[] types;
    private final byte[] signs;
    private final SplittableRandom random = new SplittableRandom(0x4e6575726fL);
    private volatile Snapshot latest = new Snapshot(0, 0, 0, 0, 0, 0, 0, false, Map.of(), new int[0]);
    private volatile int firingNeuronSampleLimit = MAX_FIRING_NEURON_IDS;
    private long tick;

    public FlyBrain(ConnectomeData graph) {
        this(graph, new long[0]);
    }

    public FlyBrain(ConnectomeData graph, long[] learnedSynapses) {
        this.graph = graph;
        plasticity = new SynapticPlasticity(learnedSynapses, graph.connections());
        incoming = graph.incomingIndex();
        int n = graph.neurons();
        lastSpikeSubstep = new long[n];
        java.util.Arrays.fill(lastSpikeSubstep, Long.MIN_VALUE);
        voltage = new double[n]; current = new double[n]; refractory = new byte[n];
        sampledFiringNeurons = new BitSet(n);
        active = new NeuronQueue(n); next = new NeuronQueue(n);
        for (int i = 0; i < n; i++) voltage[i] = REST_MV;
        sensory = graph.visualReceptors;
        leftEye = graph.visualLeftReceptors;
        rightEye = graph.visualRightReceptors;
        olfactory = graph.olfactoryNeurons;
        gustatory = graph.gustatoryNeurons;
        tactile = graph.tactileNeurons;
        types = graph.neuronTypes;
        signs = graph.transmitterSigns;
    }

    public Snapshot latest() { return latest; }
    public long[] learnedSynapses() { return plasticity.snapshot(); }
    public int learnedSynapseCount() { return plasticity.learnedSynapseCount(); }
    public int learningRevision() { return plasticity.revision(); }
    public void setFiringNeuronSampleLimit(int limit) {
        firingNeuronSampleLimit = Math.max(0, Math.min(MAX_FIRING_NEURON_IDS, limit));
    }

    /** Queue a copied learned-connectome imprint; applied at the next neural step. */
    public void copyLearnedSynapses(long[] state) {
        pendingMindCopy.set(java.util.Arrays.copyOf(state, state.length));
    }

    /** Advance 50 ms of neural time. The call is intended for a dedicated worker thread, never the Minecraft tick. */
    public Snapshot advance(Drive drive) {
        long[] copiedMind = pendingMindCopy.getAndSet(null);
        if (copiedMind != null) {
            plasticity.replace(copiedMind, graph.connections());
            java.util.Arrays.fill(lastSpikeSubstep, Long.MIN_VALUE);
        }
        long spikes = 0;
        long synapticEvents = 0;
        int sampleLimit = firingNeuronSampleLimit;
        int[] firingNeuronIds = new int[Math.min(256, sampleLimit)];
        int firingNeuronCount = 0;
        sampledFiringNeurons.clear();
        Map<String, Integer> counts = new HashMap<>();
        for (int sub = 0; sub < 100; sub++) {
            long neuralSubstep = tick * 100 + sub;
            if (leftEye.length > 0 && rightEye.length > 0) {
                inject(leftEye, (drive.light + drive.leftEye) * 35 + drive.looming * 45);
                inject(rightEye, (drive.light + drive.rightEye) * 35 + drive.looming * 45);
            } else {
                inject(sensory, drive.light * 70 + drive.looming * 90);
            }
            inject(olfactory, drive.odor * 80);
            inject(gustatory, drive.taste * 100 + drive.reward * 200);
            inject(tactile, drive.tactile * 80 + drive.sound * 60 + drive.pain * 200);
            for (int k = 0; k < active.size(); k++) {
                int id = active.get(k);
                if (refractory[id] > 0) {
                    refractory[id]--;
                    next.add(id);
                    continue;
                }
                double v = voltage[id] + (REST_MV - voltage[id] + current[id]) * DT_MS / TAU_MS;
                current[id] *= Math.exp(-DT_MS / SYNAPSE_TAU_MS);
                if (v >= THRESHOLD_MV) {
                    voltage[id] = RESET_MV;
                    refractory[id] = 4;
                    next.add(id);
                    spikes++;
                    // Reward eligibility requires a presynaptic spike before this postsynaptic spike.
                    for (int incomingPosition = incoming.offsets()[id]; incomingPosition < incoming.offsets()[id + 1]; incomingPosition++) {
                        int source = incoming.sources()[incomingPosition];
                        long age = neuralSubstep - lastSpikeSubstep[source];
                        if (age > 0 && age <= 20 && signs[source] != 0)
                            plasticity.record(incoming.edges()[incomingPosition], tick + 1);
                    }
                    lastSpikeSubstep[id] = neuralSubstep;
                    if (firingNeuronCount < sampleLimit && !sampledFiringNeurons.get(id)) {
                        sampledFiringNeurons.set(id);
                        if (firingNeuronCount == firingNeuronIds.length)
                            firingNeuronIds = java.util.Arrays.copyOf(firingNeuronIds,
                                    Math.min(MAX_FIRING_NEURON_IDS, firingNeuronIds.length * 2));
                        firingNeuronIds[firingNeuronCount++] = id;
                    }
                    String type = types[id];
                    if (type.startsWith("DN") || type.startsWith("MN") || type.startsWith("BDN")
                            || type.startsWith("oDN1")) counts.merge(type, 1, Integer::sum);
                    for (int edge = graph.rowOffsets[id]; edge < graph.rowOffsets[id + 1]; edge++) {
                        int to = graph.targets[edge];
                        double input = signedSynapticInput(graph.connectionWeights[edge], signs[id],
                                plasticity.weightScale(edge));
                        if (input != 0) {
                            current[to] += input;
                            next.add(to);
                        }
                    }
                    synapticEvents += graph.rowOffsets[id + 1] - graph.rowOffsets[id];
                } else {
                    voltage[id] = v;
                    if (Math.abs(v - REST_MV) > 0.03 || Math.abs(current[id]) > 0.03)
                        next.add(id);
                }
            }
            active.clear();
            NeuronQueue swap = active; active = next; next = swap;
        }
        double fwd = 0, left = 0, right = 0, lift = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String type = e.getKey();
            if (type.startsWith("DNp09") || type.startsWith("BDN") || type.startsWith("oDN1")) fwd += e.getValue();
            if (type.startsWith("DNa02") && (type.endsWith("L") || type.contains("_L"))) left += e.getValue();
            if (type.startsWith("DNa02") && (type.endsWith("R") || type.contains("_R"))) right += e.getValue();
            if (type.startsWith("DNg02")) lift += e.getValue();
        }
        double driveForward = Math.min(1, fwd / 8.0);
        double turn = Math.max(-1, Math.min(1, (right - left) / 5.0));
        double liftDrive = Math.min(1, lift / 8.0);
        boolean escape = counts.entrySet().stream().anyMatch(e -> e.getKey().startsWith("DNp01") && e.getValue() > 0);
        Map<String, Double> rates = Map.of("DNp09/BDN-forward", driveForward, "DNa02-turn", turn, "DNg02-lift", liftDrive,
                "DNp01-escape", escape ? 1.0 : 0.0);
        plasticity.applyFeedback(drive.reward, drive.pain, tick + 1);
        latest = new Snapshot(++tick, spikes, synapticEvents, active.size(), driveForward, turn, liftDrive,
                escape, rates, java.util.Arrays.copyOf(firingNeuronIds, firingNeuronCount));
        return latest;
    }

    private void inject(int[] ids, double hz) {
        double chance = hz * DT_MS / 1000.0;
        if (chance <= 0) return;
        for (int id : ids) if (random.nextDouble() < chance && refractory[id] == 0) {
            // Sensory events enter only through annotated sensory populations; no motor neuron is stimulated directly.
            voltage[id] = THRESHOLD_MV + 1.0;
            next.add(id);
        }
    }

    static double signedSynapticInput(short synapseCount, byte polarity, double plasticityScale) {
        if (polarity == 0) return 0;
        double sign = polarity < 0 ? -1 : 1;
        return Short.toUnsignedInt(synapseCount) * plasticityScale * SYNAPSE_SCALE_MV * sign;
    }
}
