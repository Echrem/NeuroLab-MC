package lab.neurolab.minecraft;

import lab.neurolab.brain.ControlMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NeuroMonitorActionPayload(BlockPos position, Action action, ControlMode mode, String profileName,
                                        int targetEntityId, java.util.UUID targetUuid)
        implements CustomPacketPayload {
    public enum Action { ATTACH_NEAREST, SET_MODE, DETACH, END_TRIAL, SAVE_MIND, APPLY_MIND, KILL_SUBJECT }
    public NeuroMonitorActionPayload(BlockPos position, Action action, ControlMode mode) {
        this(position, action, mode, "", -1, null);
    }
    public NeuroMonitorActionPayload(BlockPos position, Action action, ControlMode mode, String profileName) {
        this(position, action, mode, profileName, -1, null);
    }
    public NeuroMonitorActionPayload(BlockPos position, Action action, ControlMode mode, String profileName,
                                     int targetEntityId) {
        this(position, action, mode, profileName, targetEntityId, null);
    }
    public static final Type<NeuroMonitorActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeuroLabMod.MOD_ID, "monitor_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeuroMonitorActionPayload> CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeBlockPos(payload.position());
                        buffer.writeEnum(payload.action());
                        buffer.writeEnum(payload.mode());
                        buffer.writeUtf(payload.profileName(), 32);
                        buffer.writeInt(payload.targetEntityId());
                        buffer.writeBoolean(payload.targetUuid() != null);
                        if (payload.targetUuid() != null) buffer.writeUUID(payload.targetUuid());
                    }, buffer -> new NeuroMonitorActionPayload(buffer.readBlockPos(),
                            buffer.readEnum(Action.class), buffer.readEnum(ControlMode.class), buffer.readUtf(32),
                            buffer.readInt(), buffer.readBoolean() ? buffer.readUUID() : null));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
