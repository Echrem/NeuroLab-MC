package lab.neurolab.minecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Camera-scoped copy of an observation, sent even when the normal tracker feed is unavailable. */
public record CameraTelemetryPayload(BrainTelemetryPayload sample) implements CustomPacketPayload {
    public static final Type<CameraTelemetryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("neurolab", "camera_telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraTelemetryPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> BrainTelemetryPayload.CODEC.encode(buffer, payload.sample()),
            buffer -> new CameraTelemetryPayload(BrainTelemetryPayload.CODEC.decode(buffer)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
