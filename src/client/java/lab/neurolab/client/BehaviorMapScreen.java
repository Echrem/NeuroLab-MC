package lab.neurolab.client;

import lab.neurolab.brain.BehaviorCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Readable distinction between prewired reflexes, body assistance, and learned preferences. */
final class BehaviorMapScreen extends Screen {
    private final Screen parent;
    private int scroll;

    BehaviorMapScreen(Screen parent) {
        super(Component.literal("NeuroLab · Behavior Map"));
        this.parent = parent;
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF080D13);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF080D13);
        g.fill(12, 12, width - 12, height - 12, 0xFF17232E);
        g.fill(12, 12, width - 12, 14, 0xFF49BCE4);
        g.drawString(font, "BEHAVIOR MAP", 25, 23, 0xFFB9ECFF, true);
        g.drawString(font, font.plainSubstrByWidth("Automatic ≠ learned · assisted cues are not connectome anatomy",
                width - 50), 25, 38, 0xFFB7C8D4, false);
        int listTop = 59, listBottom = height - 32;
        g.fill(20, listTop, width - 20, listBottom, 0xFF0B121A);
        g.enableScissor(20, listTop, width - 20, listBottom);
        int y = listTop + 9 - scroll;
        BehaviorCatalog.Layer previous = null;
        for (BehaviorCatalog.Entry entry : BehaviorCatalog.entries()) {
            if (entry.layer() != previous) {
                if (previous != null) y += 8;
                g.drawString(font, entry.layer().name().replace('_', ' ') + " BEHAVIORS", 31, y,
                        color(entry.layer()), true);
                y += 18;
                previous = entry.layer();
            }
            if (y + 25 >= listTop && y < listBottom) {
                g.fill(28, y - 2, width - 28, y + 23, 0xFF1B2A35);
                g.drawString(font, font.plainSubstrByWidth(entry.name(), width - 70), 37, y,
                        0xFFE5F1F8, false);
                g.drawString(font, font.plainSubstrByWidth(entry.mechanism(), width - 70), 37, y + 12,
                        0xFF9EB8C8, false);
            }
            y += 29;
        }
        g.disableScissor();
        g.drawString(font, "Wheel: scroll · Esc: monitor · categories describe code behavior, not biology",
                24, height - 24, 0xFFB7C8D4, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int color(BehaviorCatalog.Layer layer) {
        return switch (layer) {
            case AUTOMATIC -> 0xFFFFC98B;
            case ASSISTED -> 0xFF83D5F0;
            case LEARNED -> 0xFF8CE5AD;
        };
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int contentHeight = BehaviorCatalog.entries().size() * 29 + BehaviorCatalog.Layer.values().length * 26 + 16;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (height - 91)),
                scroll - (int) Math.signum(vertical) * 30));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
