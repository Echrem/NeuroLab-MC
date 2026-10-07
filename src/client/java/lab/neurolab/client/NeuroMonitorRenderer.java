package lab.neurolab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import lab.neurolab.minecraft.NeuroMonitorBlock;
import lab.neurolab.minecraft.NeuroMonitorBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Small 3D telemetry display rendered on the monitor's screen face. */
public final class NeuroMonitorRenderer implements BlockEntityRenderer<NeuroMonitorBlockEntity> {
    private final Font font;

    public NeuroMonitorRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(NeuroMonitorBlockEntity monitor, float partialTick, PoseStack pose,
                      MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Direction facing = monitor.getBlockState().getValue(NeuroMonitorBlock.FACING);
        pose.pushPose();
        float yaw = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5, -0.5, -0.5);
        pose.translate(0.20, 0.70, 0.061);
        pose.scale(0.0054f, -0.0054f, 0.0054f);

        Entity subject = linkedSubject(monitor);
        BrainTelemetryPayload sample = subject == null ? null : TelemetryHistory.latest(subject.getId());
        line(pose, buffers, "NEUROLAB · LIVE", 0, 0, 0xFF55E7F5, packedLight);
        if (sample == null) {
            line(pose, buffers, subject == null ? "NO SUBJECT LINKED" : "WAITING FOR SIGNAL", 0, 12,
                    0xFFFFCF78, packedLight);
            line(pose, buffers, subject == null ? "USE LINK SUBJECT" : "CHECK CONSOLE", 0, 24,
                    0xFFB6C8D3, packedLight);
            line(pose, buffers, "EXPERIMENT CHAIR", 0, 40, 0xFF7A9AAA, packedLight);
        } else {
            String name = subject.getName().getString().toUpperCase(java.util.Locale.ROOT);
            if (name.length() > 12) name = name.substring(0, 12);
            line(pose, buffers, name, 0, 11,
                    0xFFE8F3F9, packedLight);
            line(pose, buffers, "SPIKES " + compact(sample.spikes()), 0, 22, 0xFFE8F3F9, packedLight);
            line(pose, buffers, "SYN " + compact(sample.synapticEvents()) + " / 50ms",
                    0, 34, 0xFF65F3C4, packedLight);
            line(pose, buffers, String.format(java.util.Locale.ROOT, "PAIN %02.0f  REW %02.0f",
                    sample.pain() * 100, sample.reward() * 100), 0, 46, 0xFFFFBE84, packedLight);
            line(pose, buffers, String.format(java.util.Locale.ROOT, "FWD %02.0f  LIFT %02.0f",
                    sample.bodyForward() * 100, sample.bodyLift() * 100), 0, 58, 0xFFC7D7E0, packedLight);
        }
        pose.popPose();
    }

    private Entity linkedSubject(NeuroMonitorBlockEntity monitor) {
        if (monitor.getLevel() instanceof ClientLevel level && monitor.subject() != null) {
            for (Entity entity : level.entitiesForRendering())
                if (entity.getUUID().equals(monitor.subject())) return entity;
        }
        return null;
    }

    private void line(PoseStack pose, MultiBufferSource buffers, String text, int x, int y, int color, int light) {
        font.drawInBatch(Component.literal(text), x, y, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0x60001420, light);
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }
}
