package lab.neurolab.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNeuroMonitorPayload(BlockPos position) implements CustomPacketPayload {
    public static final Type<OpenNeuroMonitorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "open_monitor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNeuroMonitorPayload> CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeBlockPos(payload.position()),
                    buffer -> new OpenNeuroMonitorPayload(buffer.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
