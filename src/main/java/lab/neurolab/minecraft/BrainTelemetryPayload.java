package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import lab.neurolab.brain.ControlMode;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact observation packet; neural internals stay on the server. */
public record BrainTelemetryPayload(int entityId, int spikes, long synapticEvents, int active, float forward, float turn, float lift,
                                    float realTimeFactor, boolean escape, float light, float leftEye,
                                    float rightEye, float looming, float touch, float odor, float taste,
                                    float pain, float reward,
                                    float bodyForward, float bodyTurn, float bodyLift, boolean bodyEscape,
                                    boolean reflex, boolean testStimulus, ControlMode mode, float sound,
                                    float satiety, float hydration, int learnedSynapseCount, int learningRevision,
                                    int[] firingNeuronIds)
        implements CustomPacketPayload {
    public static final int MAX_FIRING_NEURON_IDS = lab.neurolab.brain.FlyBrain.MAX_FIRING_NEURON_IDS;
    public static final Type<BrainTelemetryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neurolab", "telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainTelemetryPayload> CODEC =
            StreamCodec.of(BrainTelemetryPayload::encode, BrainTelemetryPayload::decode);

    public BrainTelemetryPayload(int entityId, int spikes, int active, float forward, float turn, float lift,
                                 float realTimeFactor, boolean escape) {
        this(entityId, spikes, 0, active, forward, turn, lift, realTimeFactor, escape,
                0, 0, 0, 0, 0, 0, 0, 0, 0, forward, turn, lift, escape, false, false,
                ControlMode.ASSISTED, 0, 1, 1, 0, 0, new int[0]);
    }

    public BrainTelemetryPayload(int entityId, int spikes, long synapticEvents, int active, float forward, float turn,
                                 float lift, float realTimeFactor, boolean escape, float light, float leftEye,
                                 float rightEye, float looming, float touch, float odor, float taste, float pain,
                                 float reward, float bodyForward, float bodyTurn, float bodyLift, boolean bodyEscape,
                                 boolean reflex, boolean testStimulus, ControlMode mode) {
        this(entityId, spikes, synapticEvents, active, forward, turn, lift, realTimeFactor, escape, light, leftEye,
                rightEye, looming, touch, odor, taste, pain, reward, bodyForward, bodyTurn, bodyLift, bodyEscape,
                reflex, testStimulus, mode, 0, 1, 1, 0, 0, new int[0]);
    }

    public BrainTelemetryPayload(int entityId, int spikes, long synapticEvents, int active, float forward, float turn,
                                 float lift, float realTimeFactor, boolean escape, float light, float leftEye,
                                 float rightEye, float looming, float touch, float odor, float taste, float pain,
                                 float reward, float bodyForward, float bodyTurn, float bodyLift, boolean bodyEscape,
                                 boolean reflex, boolean testStimulus, ControlMode mode, int[] firingNeuronIds) {
        this(entityId, spikes, synapticEvents, active, forward, turn, lift, realTimeFactor, escape, light, leftEye,
                rightEye, looming, touch, odor, taste, pain, reward, bodyForward, bodyTurn, bodyLift, bodyEscape,
                reflex, testStimulus, mode, 0, 1, 1, 0, 0, firingNeuronIds);
    }

    public BrainTelemetryPayload(int entityId, int spikes, long synapticEvents, int active, float forward, float turn,
                                 float lift, float realTimeFactor, boolean escape, float light, float leftEye,
                                 float rightEye, float looming, float touch, float odor, float taste, float pain,
                                 float reward, float bodyForward, float bodyTurn, float bodyLift, boolean bodyEscape,
                                 boolean reflex, boolean testStimulus, ControlMode mode, float sound,
                                 int[] firingNeuronIds) {
        this(entityId, spikes, synapticEvents, active, forward, turn, lift, realTimeFactor, escape, light, leftEye,
                rightEye, looming, touch, odor, taste, pain, reward, bodyForward, bodyTurn, bodyLift, bodyEscape,
                reflex, testStimulus, mode, sound, 1, 1, 0, 0, firingNeuronIds);
    }

    public BrainTelemetryPayload {
        satiety = clampUnit(satiety);
        hydration = clampUnit(hydration);
        learnedSynapseCount = Math.max(0, learnedSynapseCount);
        learningRevision = Math.max(0, learningRevision);
        firingNeuronIds = java.util.Arrays.copyOf(firingNeuronIds,
                Math.min(MAX_FIRING_NEURON_IDS, firingNeuronIds.length));
    }

    public BrainTelemetryPayload withoutFiringNeuronIds() {
        if (firingNeuronIds.length == 0) return this;
        return new BrainTelemetryPayload(entityId, spikes, synapticEvents, active, forward, turn, lift,
                realTimeFactor, escape, light, leftEye, rightEye, looming, touch, odor, taste, pain, reward,
                bodyForward, bodyTurn, bodyLift, bodyEscape, reflex, testStimulus, mode, sound,
                satiety, hydration, learnedSynapseCount, learningRevision, new int[0]);
    }

    private static float clampUnit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    @Override public int[] firingNeuronIds() {
        return java.util.Arrays.copyOf(firingNeuronIds, firingNeuronIds.length);
    }

    public int firingNeuronCount() { return firingNeuronIds.length; }

    /** Read one sampled firing ID without copying the entire network packet array. */
    public int firingNeuronIdAt(int index) { return firingNeuronIds[index]; }
    public float hunger() { return 1.0f - satiety; }
    public float thirst() { return 1.0f - hydration; }

    void appendFiringNeuronIds(StringBuilder target) {
        for (int i = 0; i < firingNeuronIds.length; i++) {
            if (i > 0) target.append(',');
            target.append(firingNeuronIds[i]);
        }
    }

    private static BrainTelemetryPayload decode(RegistryFriendlyByteBuf b) {
        int entityId = b.readVarInt();
        int spikes = b.readVarInt();
        long synapticEvents = b.readVarLong();
        int active = b.readVarInt();
        float forward = b.readFloat();
        float turn = b.readFloat();
        float lift = b.readFloat();
        float realTimeFactor = b.readFloat();
        boolean escape = b.readBoolean();
        float light = b.readFloat();
        float leftEye = b.readFloat();
        float rightEye = b.readFloat();
        float looming = b.readFloat();
        float touch = b.readFloat();
        float odor = b.readFloat();
        float taste = b.readFloat();
        float pain = b.readFloat();
        float reward = b.readFloat();
        float bodyForward = b.readFloat();
        float bodyTurn = b.readFloat();
        float bodyLift = b.readFloat();
        boolean bodyEscape = b.readBoolean();
        boolean reflex = b.readBoolean();
        boolean testStimulus = b.readBoolean();
        ControlMode mode = b.readEnum(ControlMode.class);
        float sound = b.readFloat();
        float satiety = b.readFloat();
        float hydration = b.readFloat();
        int learnedSynapseCount = b.readVarInt();
        int learningRevision = b.readVarInt();
        int sampleCount = b.readVarInt();
        if (sampleCount < 0 || sampleCount > MAX_FIRING_NEURON_IDS)
            throw new IllegalArgumentException("Invalid active-neuron sample size: " + sampleCount);
        int[] firingNeuronIds = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) firingNeuronIds[i] = b.readVarInt();
        return new BrainTelemetryPayload(entityId, spikes, synapticEvents, active, forward, turn, lift, realTimeFactor, escape,
                light, leftEye, rightEye, looming, touch, odor, taste, pain, reward, bodyForward, bodyTurn, bodyLift,
                bodyEscape, reflex, testStimulus, mode, sound, satiety, hydration,
                learnedSynapseCount, learningRevision, firingNeuronIds);
    }

    private static void encode(RegistryFriendlyByteBuf b, BrainTelemetryPayload p) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.spikes); b.writeVarLong(p.synapticEvents); b.writeVarInt(p.active);
        b.writeFloat(p.forward); b.writeFloat(p.turn); b.writeFloat(p.lift); b.writeFloat(p.realTimeFactor); b.writeBoolean(p.escape);
        b.writeFloat(p.light); b.writeFloat(p.leftEye); b.writeFloat(p.rightEye); b.writeFloat(p.looming);
        b.writeFloat(p.touch); b.writeFloat(p.odor); b.writeFloat(p.taste);
        b.writeFloat(p.pain); b.writeFloat(p.reward);
        b.writeFloat(p.bodyForward); b.writeFloat(p.bodyTurn); b.writeFloat(p.bodyLift);
        b.writeBoolean(p.bodyEscape); b.writeBoolean(p.reflex); b.writeBoolean(p.testStimulus); b.writeEnum(p.mode);
        b.writeFloat(p.sound); b.writeFloat(p.satiety); b.writeFloat(p.hydration);
        b.writeVarInt(p.learnedSynapseCount); b.writeVarInt(p.learningRevision);
        int[] firingNeuronIds = p.firingNeuronIds;
        b.writeVarInt(firingNeuronIds.length);
        for (int neuronId : firingNeuronIds) b.writeVarInt(neuronId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BrainTelemetryPayload p)) return false;
        return entityId == p.entityId && spikes == p.spikes && synapticEvents == p.synapticEvents
                && active == p.active && Float.compare(forward, p.forward) == 0
                && Float.compare(turn, p.turn) == 0 && Float.compare(lift, p.lift) == 0
                && Float.compare(realTimeFactor, p.realTimeFactor) == 0 && escape == p.escape
                && Float.compare(light, p.light) == 0 && Float.compare(leftEye, p.leftEye) == 0
                && Float.compare(rightEye, p.rightEye) == 0 && Float.compare(looming, p.looming) == 0
                && Float.compare(touch, p.touch) == 0 && Float.compare(odor, p.odor) == 0
                && Float.compare(taste, p.taste) == 0 && Float.compare(pain, p.pain) == 0
                && Float.compare(reward, p.reward) == 0 && Float.compare(sound, p.sound) == 0
                && Float.compare(satiety, p.satiety) == 0 && Float.compare(hydration, p.hydration) == 0
                && learnedSynapseCount == p.learnedSynapseCount && learningRevision == p.learningRevision
                && Float.compare(bodyForward, p.bodyForward) == 0
                && Float.compare(bodyTurn, p.bodyTurn) == 0 && Float.compare(bodyLift, p.bodyLift) == 0
                && bodyEscape == p.bodyEscape && reflex == p.reflex && testStimulus == p.testStimulus
                && mode == p.mode && java.util.Arrays.equals(firingNeuronIds, p.firingNeuronIds);
    }

    @Override public int hashCode() {
        int result = java.util.Objects.hash(entityId, spikes, synapticEvents, active, forward, turn, lift,
                realTimeFactor, escape, light, leftEye, rightEye, looming, touch, odor, taste, pain, reward,
                bodyForward, bodyTurn, bodyLift, bodyEscape, reflex, testStimulus, mode, sound,
                satiety, hydration, learnedSynapseCount, learningRevision);
        return 31 * result + java.util.Arrays.hashCode(firingNeuronIds);
    }
}
