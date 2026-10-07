package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Per-edge evidence: anatomical synapse count and learned fractional change remain separate. */
public record SynapseInspectionPayload(int entityId, int neuronId, int revision, int learnedCount,
                                       float flowerCue, float waterCue, float fireCue,
                                       float zombieCue, float villagerCue, int playerThreatHits,
                                       float playerCue, String strongestBlockCue, float strongestBlockValue,
                                       int blockedCells,
                                       List<Edge> edges) implements CustomPacketPayload {
    public static final int MAX_EDGES = 24;
    public static final Type<SynapseInspectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "synapse_inspection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SynapseInspectionPayload> CODEC =
            StreamCodec.of(SynapseInspectionPayload::encode, SynapseInspectionPayload::decode);

    public SynapseInspectionPayload { edges = List.copyOf(edges.subList(0, Math.min(edges.size(), MAX_EDGES))); }

    private static SynapseInspectionPayload decode(RegistryFriendlyByteBuf b) {
        int entityId = b.readVarInt(), neuronId = b.readVarInt();
        int revision = b.readVarInt(), learnedCount = b.readVarInt();
        float flowerCue = b.readFloat(), waterCue = b.readFloat(), fireCue = b.readFloat();
        float zombieCue = b.readFloat(), villagerCue = b.readFloat();
        int playerThreatHits = b.readVarInt(); float playerCue = b.readFloat();
        String strongestBlockCue = b.readUtf(128); float strongestBlockValue = b.readFloat();
        int blockedCells = b.readVarInt(), size = b.readVarInt();
        if (size < 0 || size > MAX_EDGES) throw new IllegalArgumentException("Invalid synapse inspection size");
        List<Edge> edges = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            edges.add(new Edge(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByte(), b.readFloat()));
        return new SynapseInspectionPayload(entityId, neuronId, revision, learnedCount,
                flowerCue, waterCue, fireCue, zombieCue, villagerCue, playerThreatHits, playerCue,
                strongestBlockCue, strongestBlockValue, blockedCells, edges);
    }

    private static void encode(RegistryFriendlyByteBuf b, SynapseInspectionPayload p) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.neuronId);
        b.writeVarInt(p.revision); b.writeVarInt(p.learnedCount);
        b.writeFloat(p.flowerCue); b.writeFloat(p.waterCue); b.writeFloat(p.fireCue);
        b.writeFloat(p.zombieCue); b.writeFloat(p.villagerCue);
        b.writeVarInt(p.playerThreatHits); b.writeFloat(p.playerCue);
        b.writeUtf(p.strongestBlockCue, 128); b.writeFloat(p.strongestBlockValue);
        b.writeVarInt(p.blockedCells); b.writeVarInt(p.edges.size());
        for (Edge edge : p.edges) {
            b.writeVarInt(edge.edgeId); b.writeVarInt(edge.source); b.writeVarInt(edge.target);
            b.writeVarInt(edge.contacts); b.writeByte(edge.sign); b.writeFloat(edge.change);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public record Edge(int edgeId, int source, int target, int contacts, byte sign, float change) {}
}
