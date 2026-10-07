package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;

public final class ClientTelemetry {
    private ClientTelemetry() {}

    public static void accept(BrainTelemetryPayload payload) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(payload.entityId());
        TelemetryHistory.accept(payload, entity == null ? null : entity.getUUID().toString());
    }
}
