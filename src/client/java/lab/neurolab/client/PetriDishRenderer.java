package lab.neurolab.client;

import lab.neurolab.minecraft.PetriDishBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Procedural multi-jointed arm animation for the six-second chair procedure. */
public final class PetriDishRenderer implements BlockEntityRenderer<PetriDishBlockEntity> {
    public PetriDishRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PetriDishBlockEntity chair, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (chair.procedureStartTick() == 0 || chair.getLevel() == null) return;
        double elapsed = chair.getLevel().getGameTime() - chair.procedureStartTick() + partialTick;
        if (elapsed < 0 || elapsed >= 120) return;
        float phase = (float) (elapsed / 120.0);
        float pull = smooth((float) (elapsed / 72.0));
        float insertion = smooth((float) ((elapsed - 72.0) / 14.0));
        float retract = smooth((float) ((elapsed - 104.0) / 16.0));
        float sawWobble = elapsed >= 84 && elapsed <= 106
                ? (float) Math.sin(elapsed * 1.7) * 0.018f : 0;
        Vec3 head = localHeadPosition(chair);
        Vec3 shoulder = new Vec3(0.82, 0.84, 0.18);
        Vec3 elbow = new Vec3(0.76 - 0.17 * pull, 1.16 + Math.sin(phase * Math.PI * 4) * 0.07, 0.34);
        Vec3 wrist = new Vec3(head.x + 0.22, head.y + 0.65 - 0.42 * insertion + 0.42 * retract,
                head.z - 0.08);
        Vec3 tip = new Vec3(head.x + sawWobble, head.y + 0.20 - 0.17 * insertion + 0.28 * retract,
                head.z + sawWobble * 0.4);
        pose.pushPose();
        pose.translate(chair.getBlockPos().getX(), chair.getBlockPos().getY(), chair.getBlockPos().getZ());
        drawOctopusArms(pose, buffers, phase, pull, packedLight, packedOverlay);
        rod(pose, buffers, shoulder, elbow, 0.075f, packedLight, packedOverlay);
        rod(pose, buffers, elbow, wrist, 0.058f, packedLight, packedOverlay);
        rod(pose, buffers, wrist, tip.add(0, 0.06, 0), 0.036f, packedLight, packedOverlay);
        drawSaw(pose, buffers, tip, elapsed, packedLight, packedOverlay);
        pose.popPose();
    }

    private static void drawOctopusArms(PoseStack pose, MultiBufferSource buffers, float phase, float pull,
                                        int light, int overlay) {
        Vec3 hub = new Vec3(0.50, 0.82, 0.50);
        double sway = Math.sin(phase * Math.PI * 5.0) * 0.035;
        Vec3[] roots = {new Vec3(0.18, 0.74, 0.22), new Vec3(0.82, 0.74, 0.22),
                new Vec3(0.18, 0.74, 0.78), new Vec3(0.82, 0.74, 0.78)};
        Vec3[] tips = {new Vec3(0.25, 0.52, 0.36), new Vec3(0.75, 0.52, 0.36),
                new Vec3(0.25, 0.52, 0.64), new Vec3(0.75, 0.52, 0.64)};
        for (int i = 0; i < roots.length; i++) {
            Vec3 root = roots[i];
            double sign = i % 2 == 0 ? -1 : 1;
            Vec3 elbow = new Vec3((root.x + hub.x) * 0.5 + sign * sway,
                    1.02 + Math.sin(phase * Math.PI * 3 + i) * 0.06,
                    (root.z + hub.z) * 0.5);
            Vec3 tip = tips[i].lerp(new Vec3(0.5 + (i < 2 ? -0.16 : 0.16), 0.72, 0.5), pull * 0.22);
            rod(pose, buffers, root, elbow, 0.052f, light, overlay);
            rod(pose, buffers, elbow, tip, 0.038f, light, overlay);
            rod(pose, buffers, tip, tip.add(0, 0.10, 0), 0.026f, light, overlay);
        }
    }

    private static Vec3 localHeadPosition(PetriDishBlockEntity chair) {
        if (chair.getLevel() instanceof ClientLevel level && chair.subject() != null) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity.getUUID().equals(chair.subject()))
                    return new Vec3(entity.getX() - chair.getBlockPos().getX(),
                            entity.getY() - chair.getBlockPos().getY() + entity.getBbHeight() * 0.83,
                            entity.getZ() - chair.getBlockPos().getZ());
            }
        }
        return new Vec3(0.5, 1.16, 0.5);
    }

    private static void drawSaw(PoseStack pose, MultiBufferSource buffers, Vec3 tip, double elapsed,
                                int light, int overlay) {
        pose.pushPose();
        pose.translate(tip.x - 0.12, tip.y - 0.035, tip.z - 0.12);
        pose.mulPose(new org.joml.Quaternionf().rotationY((float) elapsed * 0.42f));
        pose.scale(0.24f, 0.045f, 0.24f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.IRON_TRAPDOOR.defaultBlockState(),
                pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.pushPose();
        pose.translate(tip.x - 0.025, tip.y - 0.13, tip.z - 0.025);
        pose.scale(0.05f, 0.12f, 0.05f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.LIGHTNING_ROD.defaultBlockState(),
                pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static void rod(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to,
                            float width, int light, int overlay) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 1.0e-4) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(0, 1, 0,
                (float) (direction.x / length), (float) (direction.y / length), (float) (direction.z / length)));
        pose.scale(width, (float) length, width);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CUT_COPPER.defaultBlockState(),
                pose, buffers, light, overlay);
        pose.popPose();
    }
}
