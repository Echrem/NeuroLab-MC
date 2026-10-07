package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Request-scoped roster of currently attached mobs in the requesting player's dimension. */
public record BrainRosterPayload(List<Entry> entries, List<lab.neurolab.brain.StructuralPathway.Step> pathway)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 2048;
    private static final int MAX_PATH_STEPS = 256;
    public static final Type<BrainRosterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "brain_roster"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainRosterPayload> CODEC =
            StreamCodec.of(BrainRosterPayload::encode, BrainRosterPayload::decode);

    public BrainRosterPayload {
        entries = List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
        pathway = List.copyOf(pathway.subList(0, Math.min(pathway.size(), MAX_PATH_STEPS)));
    }

    private static BrainRosterPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid brain roster size: " + count);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int entityId = buffer.readVarInt();
            String name = buffer.readUtf(128);
            String type = buffer.readUtf(128);
            double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
            BrainTelemetryPayload sample = buffer.readBoolean() ? BrainTelemetryPayload.CODEC.decode(buffer) : null;
            entries.add(new Entry(entityId, name, type, x, y, z, sample));
        }
        int pathCount = buffer.readVarInt();
        if (pathCount < 0 || pathCount > MAX_PATH_STEPS)
            throw new IllegalArgumentException("Invalid connectome pathway size: " + pathCount);
        List<lab.neurolab.brain.StructuralPathway.Step> pathway = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++)
            pathway.add(new lab.neurolab.brain.StructuralPathway.Step(
                    buffer.readLong(), buffer.readUtf(128), buffer.readVarInt()));
        return new BrainRosterPayload(entries, pathway);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BrainRosterPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarInt(entry.entityId);
            buffer.writeUtf(entry.name, 128);
            buffer.writeUtf(entry.type, 128);
            buffer.writeDouble(entry.x); buffer.writeDouble(entry.y); buffer.writeDouble(entry.z);
            buffer.writeBoolean(entry.sample != null);
            if (entry.sample != null) BrainTelemetryPayload.CODEC.encode(buffer, entry.sample);
        }
        buffer.writeVarInt(payload.pathway.size());
        for (var step : payload.pathway) {
            buffer.writeLong(step.bodyId());
            buffer.writeUtf(step.neuronType(), 128);
            buffer.writeVarInt(step.incomingSynapseWeight());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(int entityId, String name, String type, double x, double y, double z,
                        BrainTelemetryPayload sample) {}
}
