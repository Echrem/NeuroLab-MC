package lab.neurolab.client;

import lab.neurolab.brain.ControlMode;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import lab.neurolab.minecraft.NeuroLabEntities;
import lab.neurolab.minecraft.NeuroMonitorActionPayload;
import lab.neurolab.minecraft.NeuroMonitorBlockEntity;
import lab.neurolab.minecraft.TelemetryNetwork;
import lab.neurolab.entity.NeuroFlyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

/** Live instrument console for the placed Neuro Monitor block. */
public final class NeuroMonitorScreen extends Screen {
    private final BlockPos position;
    private int subjectId = -1;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int chartMode;
    private Button killButton;
    private UUID killArmedEntityUuid;
    private int killConfirmTicks;

    private NeuroMonitorScreen(BlockPos position) {
        super(Component.literal("NeuroLab Monitor"));
        this.position = position;
    }

    public static void open(BlockPos position) {
        Minecraft.getInstance().setScreen(new NeuroMonitorScreen(position));
    }

    @Override protected void init() {
        panelWidth = Math.max(0, Math.min(740, width - 24));
        panelHeight = Math.max(0, Math.min(470, height - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int gap = 6;
        int buttonWidth = (panelWidth - 36 - gap * 2) / 3;
        int firstRowY = panelTop + panelHeight - 93;
        int secondRowY = firstRowY + 24;
        addButton("screen.neurolab.monitor.link", 0, firstRowY,
                () -> send(NeuroMonitorActionPayload.Action.ATTACH_NEAREST), buttonWidth, gap);
        addButton("screen.neurolab.monitor.mode", 1, firstRowY, this::sendMode, buttonWidth, gap);
        addButton("screen.neurolab.monitor.view", 2, firstRowY, this::viewSubject, buttonWidth, gap);
        addButton("screen.neurolab.monitor.end", 0, secondRowY,
                () -> send(NeuroMonitorActionPayload.Action.END_TRIAL), buttonWidth, gap);
        addButton("screen.neurolab.monitor.detach", 1, secondRowY,
                () -> send(NeuroMonitorActionPayload.Action.DETACH), buttonWidth, gap);
        addButton("screen.neurolab.monitor.close", 2, secondRowY, this::onClose, buttonWidth, gap);
        int utilityRowY = firstRowY - 26;
        addButton("screen.neurolab.monitor.save_mind", 0, utilityRowY,
                () -> Minecraft.getInstance().setScreen(new NeuroMindLibraryScreen(position, this)), buttonWidth, gap);
        addButton("screen.neurolab.monitor.behavior_map", 1, utilityRowY,
                () -> Minecraft.getInstance().setScreen(new BehaviorMapScreen(this)), buttonWidth, gap);
        killButton = Button.builder(killLabel(false), button -> killSubject(button))
                .bounds(panelLeft + 18 + 2 * (buttonWidth + gap), utilityRowY, buttonWidth, 20).build();
        addRenderableWidget(killButton);
        addRenderableWidget(Button.builder(chartModeLabel(), button -> {
                    chartMode = (chartMode + 1) % 3;
                    button.setMessage(chartModeLabel());
                }).bounds(panelLeft + panelWidth - 104, panelTop + 9, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("MOB DETAILS"), button ->
                        NeuroAnalysisScreen.openSubjectDetails(position, subjectId))
                .bounds(panelLeft + panelWidth - 212, panelTop + 9, 102, 20).build());
        if (panelWidth >= 600) addRenderableWidget(Button.builder(hudLabel(), button -> {
                    DashboardPreferences.toggleViewOverlay();
                    button.setMessage(hudLabel());
                }).bounds(panelLeft + panelWidth - 310, panelTop + 9, 88, 20).build());
    }

    private void addButton(String key, int column, int y, Runnable action, int buttonWidth, int gap) {
        int x = panelLeft + 18 + column * (buttonWidth + gap);
        addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, buttonWidth, 20).build());
    }

    private void send(NeuroMonitorActionPayload.Action action) {
        TelemetryNetwork.monitorAction(position, action, ControlMode.ASSISTED);
    }

    private void sendMode() {
        BrainTelemetryPayload telemetry = currentTelemetry();
        ControlMode current = telemetry == null ? ControlMode.ASSISTED : telemetry.mode();
        ControlMode[] modes = ControlMode.values();
        send(NeuroMonitorActionPayload.Action.SET_MODE, modes[(current.ordinal() + 1) % modes.length]);
    }

    private void viewSubject() {
        if (subjectId < 0) {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("screen.neurolab.monitor.no_subject_message"), true);
            return;
        }
        TelemetryNetwork.requestView(subjectId);
        onClose();
    }

    private void send(NeuroMonitorActionPayload.Action action, ControlMode mode) {
        TelemetryNetwork.monitorAction(position, action, mode);
    }

    private void killSubject(Button button) {
        var level = Minecraft.getInstance().level;
        NeuroMonitorBlockEntity monitor = level != null
                && level.getBlockEntity(position) instanceof NeuroMonitorBlockEntity be ? be : null;
        UUID targetUuid = monitor == null ? null : monitor.subject();
        if (targetUuid == null) return;
        if (targetUuid.equals(killArmedEntityUuid)
                && killConfirmTicks > 0) {
            TelemetryNetwork.monitorAction(position, NeuroMonitorActionPayload.Action.KILL_SUBJECT,
                    ControlMode.ASSISTED, -1, targetUuid);
            killArmedEntityUuid = null;
            killConfirmTicks = 0;
            button.setMessage(killLabel(false));
            return;
        }
        killArmedEntityUuid = targetUuid;
        killConfirmTicks = 60;
        button.setMessage(killLabel(true));
    }

    private static Component killLabel(boolean confirm) {
        return Component.literal(label(confirm ? "screen.neurolab.monitor.kill_confirm"
                : "screen.neurolab.monitor.kill"))
                .withStyle(confirm ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED);
    }

    @Override public void tick() {
        super.tick();
        if (killConfirmTicks > 0 && --killConfirmTicks == 0) {
            killArmedEntityUuid = null;
            if (killButton != null) killButton.setMessage(killLabel(false));
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x8C05090D);
        g.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, 0xFF45677A);
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF20B1219);
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 3, 0xFF54C8E8);
        int x = panelLeft + 15;
        int y = panelTop + 13;
        g.drawString(font, panelWidth < 400 ? "NEUROLAB" : label("screen.neurolab.monitor.title"),
                x, y, 0xFFBCEFFF, false);

        NeuroMonitorBlockEntity monitor = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(position) instanceof NeuroMonitorBlockEntity be ? be : null;
        Entity subject = resolveSubject(monitor);
        if (killButton != null) {
            UUID linkedSubject = monitor == null ? null : monitor.subject();
            killButton.active = linkedSubject != null;
            if (linkedSubject == null || killArmedEntityUuid != null
                    && !killArmedEntityUuid.equals(linkedSubject)) {
                killArmedEntityUuid = null;
                killConfirmTicks = 0;
                killButton.setMessage(killLabel(false));
            }
        }
        BrainTelemetryPayload telemetry = currentTelemetry();
        int contentTop = panelTop + 40;
        int innerWidth = panelWidth - 30;
        int viewWidth = Math.min(178, innerWidth / 3);
        boolean compact = panelHeight < 370;
        int actionTop = panelTop + panelHeight - 69;
        int viewHeight = compact ? Math.max(50, actionTop - contentTop - 34) : panelHeight - 170;
        int viewX = x;
        int viewY = contentTop;
        g.fill(viewX, viewY, viewX + viewWidth, viewY + viewHeight, 0xC2192834);
        g.drawString(font, Component.translatable("screen.neurolab.monitor.subject"), viewX + 9, viewY + 7, 0xFF9EB8C8, false);
        int referencePixels = Math.max(24, Math.min(viewWidth - 24, viewHeight - 58));
        int referenceLeft = viewX + (viewWidth - referencePixels) / 2;
        int referenceTop = viewY + 25 + Math.max(0, (viewHeight - 54 - referencePixels) / 2);
        g.fill(referenceLeft - 1, referenceTop - 1, referenceLeft + referencePixels + 1,
                referenceTop + referencePixels + 1, 0xFF426071);
        g.fill(referenceLeft, referenceTop, referenceLeft + referencePixels, referenceTop + referencePixels, 0xC40D151C);
        g.fill(referenceLeft + referencePixels / 2, referenceTop + 3, referenceLeft + referencePixels / 2 + 1,
                referenceTop + referencePixels - 3, 0x334B91A5);
        g.fill(referenceLeft + 3, referenceTop + referencePixels / 2, referenceLeft + referencePixels - 3,
                referenceTop + referencePixels / 2 + 1, 0x334B91A5);
        if (subject instanceof LivingEntity living) {
            float footprint = living instanceof NeuroFlyEntity ? 1.05f
                    : Math.max(living.getBbWidth(), living.getBbHeight());
            int previewScale = Math.max(1, Math.min(256, Math.round(referencePixels / footprint)));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, referenceLeft, referenceTop,
                    referenceLeft + referencePixels, referenceTop + referencePixels, previewScale,
                    0.0f, mouseX, mouseY, living);
            String subjectName = font.plainSubstrByWidth(subject.getName().getString(), viewWidth - 12);
            g.drawCenteredString(font, subjectName, viewX + viewWidth / 2, viewY + viewHeight - 20, 0xFFF0F6FB);
        } else {
            g.drawCenteredString(font, Component.translatable("screen.neurolab.monitor.preview_empty"),
                    viewX + viewWidth / 2, viewY + viewHeight / 2, 0xFFFFD18A);
        }

        int dataX = viewX + viewWidth + 8;
        int dataWidth = innerWidth - viewWidth - 8;
        g.drawString(font, chartMode == 0 ? "NEURAL ACTIVITY · LIVE"
                        : chartMode == 1 ? "VISION INPUT · L / C / R" : "SENSORY / MOTOR SIGNALS",
                dataX + 2, contentTop - 11, 0xFF9EB8C8, false);
        g.fill(dataX, contentTop, dataX + dataWidth, contentTop + 42, 0xC2192834);
        String title = subject == null ? Component.translatable("screen.neurolab.monitor.unlinked").getString()
                : subject.getName().getString() + "  ·  " + Component.translatable("screen.neurolab.monitor.entity").getString() + " " + subjectId;
        g.drawString(font, font.plainSubstrByWidth(title, dataWidth - 20), dataX + 10, contentTop + 8,
                subject == null ? 0xFFFFD18A : 0xFFF0F6FB, false);
        String mode = telemetry == null ? "—" : Component.translatable("screen.neurolab.mode." + telemetry.mode().id()).getString();
        String signal = telemetry == null ? Component.translatable("screen.neurolab.monitor.waiting").getString()
                : Component.translatable("screen.neurolab.monitor.live").getString();
        String status = mode + " · " + signal;
        g.drawString(font, font.plainSubstrByWidth(status, dataWidth - 20), dataX + 10, contentTop + 25,
                telemetry == null ? 0xFFFFC36D : 0xFF78D9AD);

        int chartY = contentTop + 54;
        int chartH = compact ? Math.max(24, actionTop - chartY - 6) : Math.max(50, panelHeight - 270);
        drawChart(g, dataX, chartY, dataWidth, chartH, telemetry);
        int metricsY = chartY + chartH + 9;
        if (compact) {
            // The detailed subject page carries numerical rows when the Minecraft GUI is very short.
        } else if (telemetry != null) {
            String left = String.format(Locale.ROOT, "SPIKES/STEP %d    ACTIVE %d    EDGE VISITS/STEP %d    RT %.2fx",
                    telemetry.spikes(), telemetry.active(), telemetry.synapticEvents(), telemetry.realTimeFactor());
            g.drawString(font, font.plainSubstrByWidth(left, dataWidth), dataX + 2, metricsY, 0xFFE2EDF5, false);
            String right = String.format(Locale.ROOT, "%s %.2f  %s %.2f  %s %.2f  %s %.2f    F %.2f / T %+.2f / L %.2f",
                    label("screen.neurolab.monitor.eye"), telemetry.light(), label("screen.neurolab.monitor.touch"), telemetry.touch(),
                    label("screen.neurolab.monitor.odor"), telemetry.odor(), label("screen.neurolab.monitor.taste"), telemetry.taste(),
                    telemetry.bodyForward(), telemetry.bodyTurn(), telemetry.bodyLift());
            g.drawString(font, font.plainSubstrByWidth(right, dataWidth), dataX + 2, metricsY + 14, 0xFFB5C6D3, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(label("screen.neurolab.monitor.help_link"), dataWidth),
                    dataX + 2, metricsY + 3, 0xFFFFD18A, false);
        }
        if (!compact) g.drawString(font, font.plainSubstrByWidth(label("screen.neurolab.monitor.help_controls"), innerWidth),
                x + 2, panelTop + panelHeight - 136, 0xFFB5C6D3, false);
        g.drawString(font, font.plainSubstrByWidth(label("screen.neurolab.monitor.footer"), innerWidth),
                x + 2, panelTop + panelHeight - 14, 0xFF8FA5B5, false);
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }

    private static String label(String key) { return Component.translatable(key).getString(); }

    private Component chartModeLabel() {
        return Component.literal(switch (chartMode) {
            case 1 -> "VIEW · VISION";
            case 2 -> "VIEW · SENSORY";
            default -> "VIEW · NEURAL";
        });
    }

    private Component hudLabel() {
        return Component.literal(DashboardPreferences.showViewOverlay() ? "HUD · ON" : "HUD · OFF");
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Entity resolveSubject(NeuroMonitorBlockEntity monitor) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        UUID id = monitor == null ? null : monitor.subject();
        if (id != null) for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) { subjectId = entity.getId(); return entity; }
        }
        subjectId = -1;
        return null;
    }

    private BrainTelemetryPayload currentTelemetry() {
        if (subjectId < 0) return null;
        return TelemetryHistory.latest(subjectId);
    }

    private void drawChart(GuiGraphics g, int x, int y, int w, int h, BrainTelemetryPayload latest) {
        g.fill(x, y, x + w, y + h, 0xCC101A22);
        if (h < 38) {
            g.drawString(font, font.plainSubstrByWidth("Open MOB DETAILS for plots", w - 12),
                    x + 6, y + 8, 0xFF9EB8C8, false);
            return;
        }
        String chartTitle = switch (chartMode) {
            case 1 -> "VISION INPUT  /  LEFT · CENTER · RIGHT";
            case 2 -> "SENSORY + MOTOR  /  RECORDED HISTORY";
            default -> "NEURON ACTIVITY  /  RECORDED HISTORY";
        };
        g.drawString(font, font.plainSubstrByWidth(chartTitle, Math.max(1, w - 16)),
                x + 8, y + 7, 0xFF9EB8C8, false);
        int graphTop = y + 23;
        int graphBottom = y + h - 12;
        int graphH = Math.max(1, graphBottom - graphTop);
        for (int i = 0; i < 4; i++) g.fill(x + 8, graphTop + i * graphH / 3, x + w - 8,
                graphTop + i * graphH / 3 + 1, 0x55496878);
        if (latest == null) {
            g.drawCenteredString(font, "NO SIGNAL", x + w / 2, graphTop + graphH / 2, 0xFFFFC36D);
            return;
        }
        List<BrainTelemetryPayload> samples = TelemetryHistory.snapshot().stream()
                .filter(series -> series.entityId() == subjectId).findFirst().map(TelemetryHistory.SeriesView::samples).orElse(List.of());
        g.drawString(font, "n=" + samples.size() + " · simulated signals", x + 9, y + h - 10,
                0xFF9EB8C8, false);
        if (chartMode == 1) {
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9,
                    BrainTelemetryPayload::leftEye, 0xFFFFB75E);
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9,
                    BrainTelemetryPayload::light, 0xFF57C7E7);
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9,
                    BrainTelemetryPayload::rightEye, 0xFF74D5A1);
            g.drawString(font, "L", x + 11, graphTop + 2, 0xFFFFB75E, false);
            g.drawString(font, "CENTER", x + 24, graphTop + 2, 0xFF57C7E7, false);
            g.drawString(font, "R", x + 77, graphTop + 2, 0xFF74D5A1, false);
        } else if (chartMode == 2) {
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9, BrainTelemetryPayload::light, 0xFF57C7E7);
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9, BrainTelemetryPayload::touch, 0xFFFFB75E);
            plot(g, samples, graphTop, graphBottom, x + 9, x + w - 9, BrainTelemetryPayload::forward, 0xFF74D5A1);
            g.drawString(font, "EYE", x + 11, graphTop + 2, 0xFF57C7E7, false);
            g.drawString(font, "TOUCH", x + 43, graphTop + 2, 0xFFFFB75E, false);
            g.drawString(font, "MOTOR FWD", x + 89, graphTop + 2, 0xFF74D5A1, false);
        } else {
            plotRelative(g, samples, graphTop, graphBottom, x + 9, x + w - 9,
                    BrainTelemetryPayload::spikes, 0xFF57C7E7);
            plotRelative(g, samples, graphTop, graphBottom, x + 9, x + w - 9,
                    BrainTelemetryPayload::active, 0xFF75F0D1);
            g.drawString(font, "SPIKES", x + 11, graphTop + 2, 0xFF57C7E7, false);
            g.drawString(font, "ACTIVE NEURONS", x + 55, graphTop + 2, 0xFF75F0D1, false);
        }
    }

    private static void plotRelative(GuiGraphics g, List<BrainTelemetryPayload> samples, int top, int bottom,
                                     int left, int right,
                                     java.util.function.ToIntFunction<BrainTelemetryPayload> value, int color) {
        var bins = TelemetryPlotBins.calculate(samples, right - left,
                sample -> value.applyAsInt(sample));
        double maximum = 1;
        for (var bin : bins) maximum = Math.max(maximum, bin.maximum());
        drawBins(g, bins, top, bottom, left, maximum, color);
    }

    private static void plot(GuiGraphics g, List<BrainTelemetryPayload> samples, int top, int bottom,
                             int left, int right, java.util.function.ToDoubleFunction<BrainTelemetryPayload> value,
                             int color) {
        var bins = TelemetryPlotBins.calculate(samples, right - left, value);
        drawBins(g, bins, top, bottom, left, 1, color);
    }

    private static void drawBins(GuiGraphics g, TelemetryPlotBins.Bucket[] bins, int top, int bottom,
                                 int left, double scale, int color) {
        for (int i = 0; i < bins.length; i++) {
            int high = bottom - (int) (Math.clamp(bins[i].maximum() / scale, 0, 1) * (bottom - top));
            int low = bottom - (int) (Math.clamp(bins[i].minimum() / scale, 0, 1) * (bottom - top));
            g.fill(left + i, high, left + i + 1, Math.max(high + 1, low + 1), color);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
