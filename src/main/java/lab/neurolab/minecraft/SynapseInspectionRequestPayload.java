package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A bounded, on-demand read of one attached subject's learned directed edges. */
public record SynapseInspectionRequestPayload(int entityId, int neuronId) implements CustomPacketPayload {
    public static final Type<SynapseInspectionRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "synapse_inspection_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SynapseInspectionRequestPayload> CODEC =
            StreamCodec.of((b, p) -> { b.writeVarInt(p.entityId); b.writeVarInt(p.neuronId); },
                    b -> new SynapseInspectionRequestPayload(b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
