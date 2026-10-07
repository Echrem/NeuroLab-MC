package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Compact in-world neural instrumentation shown only from a brain-controlled mob camera. */
final class NeuroViewOverlay {
    private NeuroViewOverlay() {}

    static boolean shouldRender(boolean enabled, boolean screenOpen, boolean cameraIsMob,
                                boolean cameraIsPlayer) {
        return enabled && !screenOpen && cameraIsMob && !cameraIsPlayer;
    }

    static boolean shouldEnableOnCameraChange(boolean cameraChanged, boolean cameraIsMob,
                                               boolean cameraIsPlayer) {
        return cameraChanged && cameraIsMob && !cameraIsPlayer;
    }

    static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        var camera = minecraft.getCameraEntity();
        if (!(camera instanceof net.minecraft.world.entity.Mob mob)) return;
        BrainTelemetryPayload sample = ClientViewTelemetry.latest(mob.getId());
        if (!shouldRender(DashboardPreferences.showViewOverlay(), minecraft.screen != null, true,
                camera == minecraft.player)) return;

        int width = Math.min(440, Math.max(320, graphics.guiWidth() / 3));
        width = Math.min(width, graphics.guiWidth() - 16);
        int height = 232;
        int x = Math.max(4, graphics.guiWidth() - width - 8);
        int y = Math.max(4, Math.min(12, graphics.guiHeight() - height - 4));
        panel(graphics, x, y, width, height);

        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(
                        "NEUROVIEW  /  " + mob.getName().getString(), width - 110),
                x + 8, y + 6, 0xFFB9ECFF, true);
        if (sample == null) {
            graphics.drawString(minecraft.font, "CAMERA SUBJECT  ·  WAITING FOR SERVER SAMPLE",
                    x + 8, y + 31, 0xFFFFBE84, false);
            graphics.drawString(minecraft.font, "Neural telemetry will appear here.",
                    x + 8, y + 46, 0xFF9EB8C8, false);
            return;
        }

        String mode = sample.mode().name();
        drawLeft(graphics, minecraft, "MODE " + mode + (sample.reflex() ? "  ·  REFLEX" : "")
                        + (sample.testStimulus() ? "  ·  TEST INPUT" : "  ·  WORLD INPUT"),
                x, y + 23, width, 0xFF9EC8FF);
        int graphLeft = x + 8, graphRight = x + width - 8;
        int graphTop = y + 39, graphBottom = y + 69;
        graphics.fill(graphLeft, graphTop, graphRight, graphBottom, 0xFF15232D);
        graphics.drawString(minecraft.font, "SPIKES", graphLeft + 3, graphTop + 2, 0xFF9EB8C8, false);
        List<BrainTelemetryPayload> samples = ClientViewTelemetry.history(mob.getId());
        plotSpikes(graphics, samples, graphLeft + 52, graphRight - 3, graphTop + 3, graphBottom - 3);
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "ACTIVE %d  ·  SPIKES/STEP %d  ·  EDGE VISITS %d  ·  IDS %d  ·  RTF %.2fx",
                sample.active(), sample.spikes(), sample.synapticEvents(), sample.firingNeuronCount(),
                sample.realTimeFactor()), x, y + 73, width, 0xFF75F0D1);
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "NEURAL F %d%% T %+.2f L %d%%   BODY F %d%% T %+.2f L %d%%%s%s",
                Math.round(sample.forward() * 100), sample.turn(), Math.round(sample.lift() * 100),
                Math.round(sample.bodyForward() * 100), sample.bodyTurn(), Math.round(sample.bodyLift() * 100),
                sample.escape() ? "  NEURAL ESCAPE" : "", sample.bodyEscape() ? "  BODY ESCAPE" : ""),
                x, y + 87, width, 0xFFFFBE84);
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "EYES L/C/R %d/%d/%d%%",
                Math.round(sample.leftEye() * 100), Math.round(sample.light() * 100),
                Math.round(sample.rightEye() * 100)),
                x, y + 101, width, 0xFFE6EDF3);
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "LOOM %d%%  ·  TOUCH %d%%  ·  ODOR %d%%",
                Math.round(sample.looming() * 100), Math.round(sample.touch() * 100),
                Math.round(sample.odor() * 100)), x, y + 115, width, 0xFFE6EDF3);
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "TASTE %d%%  ·  SOUND %d%%", Math.round(sample.taste() * 100),
                Math.round(sample.sound() * 100)), x, y + 129, width, 0xFFE6EDF3);
        var velocity = mob.getDeltaMovement();
        drawLeft(graphics, minecraft, String.format(java.util.Locale.ROOT,
                "WORLD X %.1f  Y %.1f  Z %.1f  ·  CLIENT VEL %.2f b/s  ·  %s",
                mob.getX(), mob.getY(), mob.getZ(), velocity.length() * 20.0,
                mob.onGround() ? "GROUNDED" : "AIRBORNE"), x, y + 143, width, 0xFFB9D5E8);

        int half = (width - 24) / 2;
        drawGauge(graphics, minecraft, "HUNGER", sample.hunger(), x + 8, y + 161, half, 0xFFFFB75E);
        drawGauge(graphics, minecraft, "THIRST", sample.thirst(), x + 16 + half, y + 161, half, 0xFF57C7E7);
        drawGauge(graphics, minecraft, "REWARD", sample.reward(), x + 8, y + 185, half, 0xFF74D5A1);
        drawGauge(graphics, minecraft, "PAIN", sample.pain(), x + 16 + half, y + 185, half, 0xFFFF766F);
        drawLeft(graphics, minecraft, "LEARNED EDGES " + sample.learnedSynapseCount()
                        + "  ·  REV " + sample.learningRevision(),
                x, y + 211, width, 0xFF9EC8FF);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF385366);
        graphics.fill(x, y, x + width, y + height, 0xE809121A);
        graphics.fill(x, y, x + 2, y + height, 0xFF49BCE4);
        graphics.fill(x + 6, y + 18, x + width - 6, y + 19, 0x55496878);
    }

    private static void drawLeft(GuiGraphics graphics, Minecraft minecraft, String text,
                                 int x, int y, int panelWidth, int color) {
        String visible = minecraft.font.plainSubstrByWidth(text, Math.max(64, panelWidth - 16));
        graphics.drawString(minecraft.font, visible, x + 8, y, color, false);
    }

    private static void drawGauge(GuiGraphics graphics, Minecraft minecraft, String label, float value,
                                  int x, int y, int width, int color) {
        float bounded = Math.max(0, Math.min(1, value));
        graphics.drawString(minecraft.font, label + " " + Math.round(bounded * 100) + "%",
                x, y, color, false);
        int top = y + 10;
        graphics.fill(x, top, x + width, top + 4, 0xFF263744);
        int filled = Math.round(width * bounded);
        if (filled > 0) graphics.fill(x, top, x + filled, top + 4, color);
    }

    private static void plotSpikes(GuiGraphics graphics, List<BrainTelemetryPayload> samples,
                                   int left, int right, int top, int bottom) {
        if (samples.size() < 2 || right <= left || bottom <= top) return;
        int start = Math.max(0, samples.size() - 36);
        int count = samples.size() - start;
        int maximum = 1;
        for (int i = start; i < samples.size(); i++) maximum = Math.max(maximum, samples.get(i).spikes());
        int previousX = left;
        int previousY = spikeY(samples.get(start).spikes(), maximum, top, bottom);
        for (int i = 1; i < count; i++) {
            int currentX = left + i * (right - left) / (count - 1);
            int currentY = spikeY(samples.get(start + i).spikes(), maximum, top, bottom);
            line(graphics, previousX, previousY, currentX, currentY, 0xFF75F0D1);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static int spikeY(int spikes, int maximum, int top, int bottom) {
        return bottom - Math.round((float) Math.max(0, spikes) / maximum * (bottom - top));
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }
}
