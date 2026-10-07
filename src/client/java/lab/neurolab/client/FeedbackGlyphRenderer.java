package lab.neurolab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.RenderLivingEvent;
import java.util.Map;
import java.util.WeakHashMap;

/** Camera-facing feedback markers above tracked, brain-attached subjects. */
final class FeedbackGlyphRenderer {
    private static final Map<Mob, GlyphState> ACTIVE = new WeakHashMap<>();
    private static final int DISPLAY_TICKS = 56;

    private FeedbackGlyphRenderer() {}

    static void render(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        Minecraft minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        if (camera == null || camera.distanceToSqr(mob) > 32 * 32) return;
        BrainTelemetryPayload sample = TelemetryHistory.latest(mob.getId());
        if (sample == null) return;
        GlyphState state = ACTIVE.computeIfAbsent(mob, ignored -> new GlyphState());
        if (sample != state.lastSample) {
            if (sample.reward() > 0.035f && state.rewardRearmed) {
                state.rewardUntil = mob.tickCount + DISPLAY_TICKS;
                state.rewardRearmed = false;
            }
            if (sample.pain() > 0.05f && state.painRearmed) {
                state.painUntil = mob.tickCount + DISPLAY_TICKS;
                state.painRearmed = false;
            }
            if (sample.reward() < 0.02f) state.rewardRearmed = true;
            if (sample.pain() < 0.025f) state.painRearmed = true;
            state.lastSample = sample;
        }
        boolean reward = mob.tickCount <= state.rewardUntil;
        boolean pain = mob.tickCount <= state.painUntil;
        if (!reward && !pain) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        double bob = Math.sin((mob.tickCount + event.getPartialTick()) * 0.25) * 0.08;
        pose.translate(0, mob.getBbHeight() + 0.95 + bob, 0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.058f, -0.058f, 0.058f);
        if (reward) draw(event, pose, "+", pain ? -9 : 0, 0xFF53F18B);
        if (pain) draw(event, pose, "-", reward ? 9 : 0, 0xFFFF626B);
        pose.popPose();
    }

    private static final class GlyphState {
        private BrainTelemetryPayload lastSample;
        private int rewardUntil = Integer.MIN_VALUE;
        private int painUntil = Integer.MIN_VALUE;
        private boolean rewardRearmed = true;
        private boolean painRearmed = true;
    }

    private static void draw(RenderLivingEvent.Post<?, ?> event, PoseStack pose,
                             String glyph, int centerX, int color) {
        Font font = Minecraft.getInstance().font;
        float x = centerX - font.width(glyph) / 2f;
        font.drawInBatch(glyph, x + 1, 1, 0xD9000000, false, pose.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(glyph, x, 0, color, false, pose.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }
}
