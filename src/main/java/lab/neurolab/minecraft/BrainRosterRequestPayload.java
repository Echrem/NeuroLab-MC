package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request for the current attached-mob roster in its present dimension. */
public record BrainRosterRequestPayload() implements CustomPacketPayload {
    public static final Type<BrainRosterRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "brain_roster_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BrainRosterRequestPayload> CODEC =
            StreamCodec.unit(new BrainRosterRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
