package lab.neurolab.client;

import lab.neurolab.brain.ControlMode;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import lab.neurolab.minecraft.NeuroMonitorActionPayload;
import lab.neurolab.minecraft.NeuroMonitorBlockEntity;
import lab.neurolab.minecraft.TelemetryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Named, monitor-local snapshots of reward-modulated connectome edge changes. */
final class NeuroMindLibraryScreen extends Screen {
    private final BlockPos position;
    private final Screen parent;
    private EditBox nameField;
    private String selectedName = "";
    private int left, top, panelWidth, panelHeight;

    NeuroMindLibraryScreen(BlockPos position, Screen parent) {
        super(Component.literal("NeuroLab · Mind Imprints"));
        this.position = position;
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(470, width - 24);
        panelHeight = Math.min(330, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        nameField = new EditBox(font, left + 18, top + 56, panelWidth - 36, 20,
                Component.literal("Imprint name"));
        nameField.setMaxLength(32);
        nameField.setHint(Component.literal("Name this learned-synapse copy"));
        addRenderableWidget(nameField);
        int buttonWidth = (panelWidth - 48) / 2;
        int buttonY = top + panelHeight - 34;
        addRenderableWidget(Button.builder(Component.literal("SAVE CURRENT AS"), button -> save())
                .bounds(left + 18, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("APPLY SELECTED"), button -> apply())
                .bounds(left + 30 + buttonWidth, buttonY, buttonWidth, 20).build());
    }

    private void save() {
        String name = nameField.getValue().trim();
        TelemetryNetwork.monitorAction(position, NeuroMonitorActionPayload.Action.SAVE_MIND,
                ControlMode.ASSISTED, name);
        selectedName = name;
    }

    private void apply() {
        String name = selectedName.isBlank() ? nameField.getValue().trim() : selectedName;
        if (name.isBlank()) return;
        TelemetryNetwork.monitorAction(position, NeuroMonitorActionPayload.Action.APPLY_MIND,
                ControlMode.ASSISTED, name);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE9060A0F);
        g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF45677A);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xF20B1219);
        g.fill(left, top, left + panelWidth, top + 3, 0xFF54C8E8);
        g.drawString(font, "MIND IMPRINT LIBRARY", left + 18, top + 14, 0xFFBCEFFF, false);
        g.drawString(font, "Saves learned edge changes; the bundled connectome stays shared and unchanged.",
                left + 18, top + 32, 0xFF9EB8C8, false);
        g.drawString(font, "NAME / COPY LABEL", left + 18, top + 47, 0xFF9EB8C8, false);
        g.drawString(font, "Saved on this monitor · up to 16 named copies", left + 18,
                top + 84, 0xFF9EB8C8, false);
        List<String> names = profileNames();
        int listTop = top + 105;
        int listBottom = top + panelHeight - 46;
        g.fill(left + 18, listTop, left + panelWidth - 18, listBottom, 0xC2192834);
        if (names.isEmpty()) {
            g.drawString(font, "No saved copies yet. Attach a subject, then save its current imprint.",
                    left + 28, listTop + 12, 0xFFB7CEE0, false);
        } else {
            for (int i = 0; i < names.size() && i < 8; i++) {
                int rowY = listTop + 5 + i * 18;
                boolean selected = names.get(i).equals(selectedName);
                if (selected) g.fill(left + 22, rowY - 2, left + panelWidth - 22, rowY + 14, 0xCC24536A);
                g.drawString(font, names.get(i), left + 29, rowY, selected ? 0xFFFFFFFF : 0xFFB7CEE0, false);
            }
        }
        Entity subject = currentSubject();
        BrainTelemetryPayload telemetry = subject == null ? null : TelemetryHistory.latest(subject.getId());
        String status = subject == null ? "No monitor subject linked"
                : subject.getName().getString() + (telemetry == null ? "" : " · "
                        + telemetry.learnedSynapseCount() + " modified edges");
        g.drawString(font, font.plainSubstrByWidth(status, panelWidth - 36), left + 18,
                top + panelHeight - 44, 0xFF9EB8C8, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private List<String> profileNames() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && minecraft.level.getBlockEntity(position) instanceof NeuroMonitorBlockEntity monitor
                ? monitor.mindProfileNames() : List.of();
    }

    private Entity currentSubject() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !(minecraft.level.getBlockEntity(position) instanceof NeuroMonitorBlockEntity monitor)
                || monitor.subject() == null) return null;
        for (Entity entity : minecraft.level.entitiesForRendering())
            if (entity.getUUID().equals(monitor.subject())) return entity;
        return null;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= left + 18 && mouseX < left + panelWidth - 18
                && mouseY >= top + 105 && mouseY < top + panelHeight - 46) {
            int index = (int) ((mouseY - (top + 110)) / 18);
            List<String> names = profileNames();
            if (index >= 0 && index < names.size() && index < 8) {
                selectedName = names.get(index);
                nameField.setValue(selectedName);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameField.isFocused()) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
