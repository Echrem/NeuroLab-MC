package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;
import lab.neurolab.minecraft.TelemetryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import lab.neurolab.minecraft.NeuroMonitorActionPayload;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Selectable, multi-signal telemetry dashboard for attached mobs. */
public final class NeuroAnalysisScreen extends Screen {
    private static final int PANEL_INNER = 0xC216232F;
    private int selectedEntityId = -1;
    private List<TelemetryHistory.SeriesView> frozenStreams;
    private java.util.Map<Integer, BrainTelemetryPayload> frozenActiveSamples;
    private Button freezeButton;
    private int chartScroll;
    private int maxChartScroll;
    private int chartViewportTop;
    private int chartViewportBottom;
    private int subjectListX, subjectListY, subjectListW, subjectListBottom;
    private int connectomePreviewX, connectomePreviewY, connectomePreviewW, connectomePreviewH;
    private int subjectScroll;
    private MobListWindow subjectWindow = MobListWindow.calculate(0, 116);
    private int rosterRefreshTicks;
    private boolean detailMode;
    private boolean layoutInitialized;
    private Button detailButton;
    private Button killButton;
    private int killArmedEntityId = -1;
    private java.util.UUID killArmedEntityUuid;
    private int killConfirmTicks;
    private List<TelemetryHistory.SeriesView> visibleStreams = List.of();
    private BlockPos monitorPosition;
    private NeuroAnalysisHeaderLayout headerLayout;
    private final ConnectomeBrainPreview connectomePreview = new ConnectomeBrainPreview();

    public NeuroAnalysisScreen() {
        super(Component.translatable("screen.neurolab.analysis"));
    }

    public static void openAtMonitor(BlockPos position) {
        NeuroAnalysisScreen screen = new NeuroAnalysisScreen();
        screen.monitorPosition = position;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void openSubjectDetails(BlockPos position, int entityId) {
        NeuroAnalysisScreen screen = new NeuroAnalysisScreen();
        screen.monitorPosition = position;
        screen.selectedEntityId = entityId;
        screen.detailMode = true;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init() {
        if (!layoutInitialized && height < 320) detailMode = true;
        layoutInitialized = true;
        TelemetryNetwork.requestRoster();
        int panelY = NeuroAnalysisViewport.popupTop(height);
        headerLayout = NeuroAnalysisHeaderLayout.calculate(width, panelY, monitorPosition != null);
        freezeButton = addRenderableWidget(Button.builder(Component.literal(frozenStreams == null ? "FREEZE" : "RESUME"),
                        b -> toggleFreeze()).bounds(headerLayout.buttonX(headerLayout.freezeIndex()),
                        headerLayout.buttonY(headerLayout.freezeIndex()),
                        headerLayout.buttonWidth(headerLayout.freezeIndex()), 18).build());
        addRenderableWidget(Button.builder(hudToggleLabel(), button -> {
                    DashboardPreferences.toggleViewOverlay();
                    button.setMessage(hudToggleLabel());
                }).bounds(headerLayout.buttonX(0), headerLayout.buttonY(0),
                        headerLayout.buttonWidth(0), 18).build());
        addRenderableWidget(Button.builder(viewLabel(), b -> viewSelectedMob())
                .bounds(headerLayout.buttonX(1), headerLayout.buttonY(1), headerLayout.buttonWidth(1), 18).build());
        int connectomeIndex = headerLayout.connectomeIndex();
        addRenderableWidget(Button.builder(guideLabel(), b ->
                        Minecraft.getInstance().setScreen(new NeuroGuideScreen(this)))
                .bounds(headerLayout.buttonX(headerLayout.guideIndex()), headerLayout.buttonY(headerLayout.guideIndex()),
                        headerLayout.buttonWidth(headerLayout.guideIndex()), 18).build());
        addRenderableWidget(Button.builder(connectomeLabel(), b ->
                        Minecraft.getInstance().setScreen(new ConnectomeExplorerScreen(this, selectedEntityId)))
                .bounds(headerLayout.buttonX(connectomeIndex), headerLayout.buttonY(connectomeIndex),
                        headerLayout.buttonWidth(connectomeIndex), 18).build());
        if (monitorPosition != null) {
            addRenderableWidget(Button.builder(Component.literal("LINK"), b ->
                            TelemetryNetwork.monitorAction(monitorPosition,
                                    NeuroMonitorActionPayload.Action.ATTACH_NEAREST,
                                    lab.neurolab.brain.ControlMode.ASSISTED))
                    .bounds(headerLayout.buttonX(2), headerLayout.buttonY(2),
                            headerLayout.buttonWidth(2), 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b ->
                        Minecraft.getInstance().setScreen(new NeuroSettingsScreen(this)))
                .bounds(headerLayout.buttonX(headerLayout.settingsIndex()), headerLayout.buttonY(headerLayout.settingsIndex()),
                        headerLayout.buttonWidth(headerLayout.settingsIndex()), 18).build());
        detailButton = addRenderableWidget(Button.builder(detailLabel(), b -> {
            detailMode = !detailMode;
            chartScroll = 0;
            detailButton.setMessage(detailLabel());
        }).bounds(headerLayout.panelLeft() + headerLayout.panelWidth() - 121,
                panelY + headerLayout.contentTopOffset() + 4, 103, 18).build());
        if (monitorPosition != null && headerLayout.panelWidth() >= 500) {
            killButton = addRenderableWidget(Button.builder(Component.literal("KILL SUBJECT"), b -> {
                if (selectedEntityId < 0) return;
                var selectedEntity = Minecraft.getInstance().level == null ? null
                        : Minecraft.getInstance().level.getEntity(selectedEntityId);
                if (!(selectedEntity instanceof net.minecraft.world.entity.Mob target) || !target.isAlive()) return;
                if (killArmedEntityId == selectedEntityId && target.getUUID().equals(killArmedEntityUuid)
                        && killConfirmTicks > 0) {
                    TelemetryNetwork.monitorAction(monitorPosition,
                            NeuroMonitorActionPayload.Action.KILL_SUBJECT,
                            lab.neurolab.brain.ControlMode.ASSISTED, selectedEntityId, target.getUUID());
                    killArmedEntityId = -1;
                    killArmedEntityUuid = null;
                    killConfirmTicks = 0;
                    b.setMessage(killLabel(false));
                } else {
                    killArmedEntityId = selectedEntityId;
                    killArmedEntityUuid = target.getUUID();
                    killConfirmTicks = 60;
                    b.setMessage(killLabel(true));
                }
            }).bounds(headerLayout.panelLeft() + headerLayout.panelWidth() - 208,
                    panelY + headerLayout.contentTopOffset() + 4, 80, 18).build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (killConfirmTicks > 0 && --killConfirmTicks == 0) {
            killArmedEntityId = -1;
            killArmedEntityUuid = null;
            if (killButton != null) killButton.setMessage(killLabel(false));
        }
        if (++rosterRefreshTicks >= 20) {
            rosterRefreshTicks = 0;
            TelemetryNetwork.requestRoster();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // An opaque backdrop prevents Minecraft's menu blur from bleeding through the instrument panels.
        g.fill(0, 0, width, height, 0xFF080D12);
        int panelW = Math.max(0, Math.min(1040, width - 48));
        int panelX = (width - panelW) / 2;
        NeuroAnalysisViewport viewport = NeuroAnalysisViewport.calculate(height,
                headerLayout.contentTopOffset(), headerLayout.compact(), detailMode);
        int panelY = viewport.panelTop();
        int panelBottom = viewport.panelBottom();
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelBottom + 1, 0xFF486174);
        g.fill(panelX, panelY, panelX + panelW, panelBottom, 0xFF0B1118);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF49BCE4);

        int left = panelX + 18;
        int contentW = panelW - 36;
        g.drawString(font, font.plainSubstrByWidth("NEUROLAB", headerLayout.titleWidth()),
                left, panelY + (headerLayout.compact() ? 9 : 12), 0xFFB9ECFF, true);
        if (!headerLayout.compact())
            g.drawString(font, font.plainSubstrByWidth(
                    (frozenStreams == null ? "LIVE" : "FROZEN · display only") + " · server simulation continues",
                    Math.max(0, contentW)), left, panelY + 29, 0xFFE0EAF2, true);

        List<TelemetryHistory.SeriesView> streams = streams().stream()
                .filter(s -> !s.samples().isEmpty())
                .sorted(Comparator.comparingInt(TelemetryHistory.SeriesView::entityId)).toList();
        if (streams.isEmpty()) {
            visibleStreams = List.of();
            subjectListX = subjectListY = subjectListW = subjectListBottom = 0;
            connectomePreviewW = connectomePreviewH = 0;
            g.fill(left, panelY + headerLayout.contentTopOffset() + 8, left + contentW, panelBottom - 39, PANEL_INNER);
            g.drawCenteredString(font, "No brain telemetry received", width / 2, height / 2 - 12, 0xFFFFD48A);
            g.drawCenteredString(font, "Neuro Fly attaches on spawn; use /neurolab attach for another mob.",
                    width / 2, height / 2 + 4, 0xFFD4E0E9);
            drawFooter(g, left, panelBottom - 23, contentW, "Esc  close    ·    Ctrl + N  reopen");
            renderWidgets(g, mouseX, mouseY, partialTick);
            return;
        }
        visibleStreams = streams;

        int selectedIndex = indexOf(streams, selectedEntityId);
        if (selectedIndex < 0) selectedIndex = 0;
        TelemetryHistory.SeriesView selected = streams.get(selectedIndex);
        selectedEntityId = selected.entityId();
        BrainTelemetryPayload stored = selected.samples().getLast();
        BrainTelemetryPayload latest = frozenStreams == null
                ? java.util.Objects.requireNonNullElse(TelemetryHistory.latest(selected.entityId()), stored)
                : frozenActiveSamples.getOrDefault(selected.entityId(), stored);

        int overviewHeight = viewport.overviewHeight();
        int overviewTop = viewport.overviewTop();
        if (!detailMode) {
            drawLabOverview(g, left, overviewTop, contentW, overviewHeight,
                    streams, selectedIndex, selected, latest, mouseX, mouseY);
        } else {
            subjectListX = subjectListY = subjectListW = subjectListBottom = 0;
            connectomePreviewW = connectomePreviewH = 0;
        }

        int mobY = viewport.statusTop();
        String mobName = "Mob #" + selected.entityId();
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(selected.entityId()) != null) {
            mobName = level.getEntity(selected.entityId()).getName().getString() + "  (#" + selected.entityId() + ")";
        }
        String identity = String.format(Locale.ROOT, "%s  ·  HISTORY %d/%d",
                mobName, selected.samples().size(), TelemetryHistory.LIMIT);
        if (killButton != null) {
            killButton.setX(left + contentW - 208);
            killButton.setY(mobY + 6);
            boolean targetAlive = level != null && level.getEntity(selectedEntityId) instanceof net.minecraft.world.entity.Mob target
                    && target.isAlive();
            killButton.active = targetAlive;
            var selectedTarget = level == null ? null : level.getEntity(selectedEntityId);
            if (!targetAlive || (killArmedEntityId != -1 && (killArmedEntityId != selectedEntityId
                    || !(selectedTarget instanceof net.minecraft.world.entity.Mob mob)
                    || !mob.getUUID().equals(killArmedEntityUuid)))) {
                killArmedEntityId = -1;
                killArmedEntityUuid = null;
                killConfirmTicks = 0;
            }
            killButton.setMessage(killLabel(killArmedEntityId == selectedEntityId && killConfirmTicks > 0));
        }
        detailButton.setX(left + contentW - (killButton == null ? 103 : 121));
        detailButton.setY(mobY + 6);
        int toolbarTextWidth = contentW - (killButton == null ? 130 : 220);
        String activity = String.format(Locale.ROOT,
                "SPIKES %d  ·  ACTIVE %d  ·  IDS %d  ·  FWD %.0f%%  ·  TURN %+.2f  ·  LIFT %.0f%%  ·  RT %.2fx%s",
                latest.spikes(), latest.active(), latest.firingNeuronCount(), latest.forward() * 100,
                latest.turn(), latest.lift() * 100, latest.realTimeFactor(), latest.escape() ? "  ·  ESCAPE" : "");
        int statusHeight = viewport.statusHeight();
        g.fill(left, mobY, left + contentW, mobY + statusHeight, PANEL_INNER);
        g.drawString(font, font.plainSubstrByWidth(identity, Math.max(1, toolbarTextWidth)), left + 8, mobY + 4,
                0xFFF0F6FB, false);
        g.drawString(font, font.plainSubstrByWidth(activity, Math.max(1, toolbarTextWidth)), left + 8, mobY + 17,
                latest.escape() ? 0xFFFF9F7A : 0xFFD5E4ED, false);
        if (statusHeight > 32) {
            String events = String.format(Locale.ROOT,
                    "EDGE VISITS/STEP %d · FIRING IDS (SAMPLED) %d · MODIFIED EDGES %d (REV %d)",
                    latest.synapticEvents(), latest.firingNeuronCount(), latest.learnedSynapseCount(),
                    latest.learningRevision());
            String state = String.format(Locale.ROOT,
                    "SOURCE %s · REWARD %.2f · PAIN %.2f · HUNGER %.0f%% · THIRST %.0f%% · %s",
                    latest.testStimulus() ? "TEST" : "WORLD", latest.reward(), latest.pain(),
                    latest.hunger() * 100, latest.thirst() * 100, latest.mode().name());
            g.drawString(font, font.plainSubstrByWidth(events, contentW - 16), left + 8, mobY + 35,
                    0xFFB7CEE0, false);
            g.drawString(font, font.plainSubstrByWidth(state, contentW - 16), left + 8, mobY + 48,
                    0xFFB7CEE0, false);
        }

        int chartTop = viewport.chartTop();
        int chartBottom = viewport.chartBottom();
        int gap = 8;
        boolean showSensory = DashboardPreferences.showLeftPanel();
        boolean showMotor = DashboardPreferences.showRightPanel();
        int sideW = detailMode ? 0 : contentW >= 760 ? 150 : contentW >= 460 ? 90 : 0;
        boolean leftPanel = sideW > 0 && showSensory;
        boolean rightPanel = sideW > 0 && showMotor;
        int centerX = left + (leftPanel ? sideW + gap : 0);
        int centerW = contentW - (leftPanel ? sideW + gap : 0) - (rightPanel ? sideW + gap : 0);
        List<Metric> enabledMetrics = java.util.Arrays.stream(Metric.values())
                .filter(metric -> DashboardPreferences.showChart(metric.ordinal())).toList();
        int previewColumns = centerW >= 400 ? 2 : 1;
        int previewRows = Math.max(1, (chartBottom - chartTop + gap) / 72);
        List<Metric> metrics = detailMode ? enabledMetrics
                : enabledMetrics.stream().limit(Math.min(4, previewColumns * previewRows)).toList();

        if (leftPanel) {
            drawSidePanel(g, left, chartTop, sideW, chartBottom - chartTop, "SENSORY INPUT", new String[][]{
                    {"EYE · CENTER", percent(latest.light())},
                    {"EYE · LEFT", percent(latest.leftEye())},
                    {"EYE · RIGHT", percent(latest.rightEye())},
                    {"LOOMING", percent(latest.looming())},
                    {"TOUCH", percent(latest.touch())},
                    {"ODOR", percent(latest.odor())},
                    {"TASTE", percent(latest.taste())},
                    {"SOUND / VIBRATION", percent(latest.sound())},
                    {"PAIN", percent(latest.pain())},
                    {"REWARD", percent(latest.reward())},
                    {"SOURCE", latest.testStimulus() ? "TEST PULSE" : "WORLD"}
            }, 0xFF79D6F2);
        }
        if (rightPanel) {
            drawSidePanel(g, left + contentW - sideW, chartTop, sideW, chartBottom - chartTop,
                    "MOTOR OUTPUT", new String[][]{
                            {"NEURAL FWD", percent(latest.forward())},
                            {"NEURAL TURN", String.format(Locale.ROOT, "%+.2f", latest.turn())},
                            {"NEURAL LIFT", percent(latest.lift())},
                            {"BODY FWD", percent(latest.bodyForward())},
                            {"BODY TURN", String.format(Locale.ROOT, "%+.2f", latest.bodyTurn())},
                            {"BODY LIFT", percent(latest.bodyLift())},
                            {"MODE", latest.mode().name()},
                            {"CONTROL", controlLabel(latest)},
                            {"NEURAL ESCAPE", latest.escape() ? "ACTIVE" : "idle"},
                            {"BODY ESCAPE", latest.bodyEscape() ? "ACTIVE" : "idle"},
                            {"SPIKES", Integer.toString(latest.spikes())},
                            {"ACTIVE CELLS", Integer.toString(latest.active())},
                            {"REAL-TIME", String.format(Locale.ROOT, "%.2fx", latest.realTimeFactor())}
                    }, latest.escape() ? 0xFFFF9F7A : 0xFFFFC16D);
        }
        chartViewportTop = chartTop;
        chartViewportBottom = chartBottom;
        maxChartScroll = 0;
        if (metrics.isEmpty()) {
            g.drawCenteredString(font, "Enable at least one plot in SETTINGS", centerX + centerW / 2,
                    chartTop + 12, 0xFFFFD48A);
        } else {
            int cols = centerW >= 400 ? 2 : 1;
            int rows = (metrics.size() + cols - 1) / cols;
            int chartW = Math.max(1, (centerW - gap * (cols - 1)) / cols);
            int chartH = detailMode ? Math.max(104, (chartBottom - chartTop - gap * (rows - 1)) / rows)
                    : Math.max(60, (chartBottom - chartTop - gap * (rows - 1)) / rows);
            maxChartScroll = Math.max(0, rows * (chartH + gap) - gap - (chartBottom - chartTop));
            chartScroll = Math.min(chartScroll, maxChartScroll);
            g.enableScissor(centerX, chartTop, centerX + centerW, Math.max(chartTop, chartBottom));
            for (int i = 0; i < metrics.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int chartX = centerX + col * (chartW + gap);
                int chartY = chartTop + row * (chartH + gap) - chartScroll;
                if (chartY + chartH <= chartTop || chartY >= chartBottom) continue;
                drawChart(g, chartX, chartY, chartW, chartH, selected.samples(), metrics.get(i));
            }
            g.disableScissor();
        }

        String nav = streams.size() > 1
                ? "← / → select mob  ·  " + (selectedIndex + 1) + " of " + streams.size() + " tracked"
                : "1 tracked mob";
        drawFooter(g, left, panelBottom - 19, contentW, nav + (detailMode
                ? " · Wheel / PgUp/PgDn scroll plots · Space freeze · Esc close"
                : " · DETAILS for all plots · V view mob · Space freeze · Esc close"));
        renderWidgets(g, mouseX, mouseY, partialTick);
    }

    private void drawLabOverview(GuiGraphics g, int x, int y, int w, int h,
                                 List<TelemetryHistory.SeriesView> streams, int selectedIndex,
                                 TelemetryHistory.SeriesView selected, BrainTelemetryPayload sample,
                                 int mouseX, int mouseY) {
        boolean compact = w < 420;
        int listW = compact ? 68 : Math.max(108, Math.min(158, w / 5));
        int previewW = compact ? 72 : Math.max(132, Math.min(190, w / 4));
        int gap = compact ? 5 : 7;
        int routeW = w - listW - previewW - gap * 2;
        int listX = x;
        int previewX = listX + listW + gap;
        int routeX = previewX + previewW + gap;
        connectomePreviewX = routeX;
        connectomePreviewY = y;
        connectomePreviewW = Math.max(0, routeW);
        connectomePreviewH = h;

        subjectListX = listX;
        subjectListY = y + 20;
        subjectListW = listW;
        subjectListBottom = y + h;
        g.fill(listX, y, listX + listW, y + h, 0xFF111D28);
        g.fill(listX, y, listX + 2, y + h, 0xFF55C7E8);
        subjectWindow = MobListWindow.calculate(streams.size(), h);
        subjectScroll = subjectWindow.clampFirstIndex(subjectScroll);
        g.drawString(font, "ATTACHED · " + streams.size(), listX + 8, y + 5, 0xFF9DE5FA, true);
        int rowY = y + 20;
        g.enableScissor(listX, rowY, listX + listW, y + h - 2);
        for (int visibleIndex = 0; visibleIndex < subjectWindow.visibleCount(); visibleIndex++) {
            int i = subjectScroll + visibleIndex;
            if (i >= streams.size()) break;
            var entry = streams.get(i);
            var entity = Minecraft.getInstance().level == null ? null
                    : Minecraft.getInstance().level.getEntity(entry.entityId());
            var rosterEntry = TelemetryRoster.snapshot().stream()
                    .filter(rosterSubject -> rosterSubject.entityId() == entry.entityId()).findFirst().orElse(null);
            String name = rosterEntry != null ? rosterEntry.name()
                    : entity == null ? "Mob #" + entry.entityId() : entity.getName().getString();
            if (entity == null && rosterEntry != null) name += " · " + rosterEntry.type();
            int color = i == selectedIndex ? 0xFF25495A : 0xFF182633;
            g.fill(listX + 5, rowY, listX + listW - 5, rowY + subjectWindow.rowHeight() - 2, color);
            g.drawString(font, font.plainSubstrByWidth((i == selectedIndex ? "› " : "  ") + name,
                    listW - 18), listX + 9, rowY + 4, i == selectedIndex ? 0xFFFFFFFF : 0xFFD1DCE5, true);
            rowY += subjectWindow.rowHeight();
        }
        g.disableScissor();

        g.fill(previewX, y, previewX + previewW, y + h, 0xFF111D28);
        g.drawString(font, "SUBJECT PREVIEW", previewX + 7, y + 5, 0xFF9DE5FA, true);
        int viewSize = Math.min(previewW - 18, h - 37);
        int viewX = previewX + (previewW - viewSize) / 2;
        int viewY = y + 22;
        g.fill(viewX, viewY, viewX + viewSize, viewY + viewSize, 0xFF080E14);
        var subject = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(selected.entityId());
        if (subject instanceof net.minecraft.world.entity.LivingEntity living) {
            float scale = living instanceof lab.neurolab.entity.NeuroFlyEntity ? 1.05f
                    : Math.max(living.getBbWidth(), living.getBbHeight());
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, viewX, viewY, viewX + viewSize,
                    viewY + viewSize, Math.max(1, Math.min(128, Math.round(viewSize / scale))),
                    0.0f, mouseX, mouseY, living);
        } else {
            g.drawCenteredString(font, "not in range", viewX + viewSize / 2, viewY + viewSize / 2 - 4, 0xFFFFD18A);
        }
        g.drawString(font, String.format(Locale.ROOT, "L %02.0f  C %02.0f  R %02.0f",
                        sample.leftEye() * 100, sample.light() * 100, sample.rightEye() * 100),
                previewX + 7, y + h - 13, 0xFFD3E4EF, true);

        if (routeW <= 0) return;
        g.fill(routeX, y, routeX + routeW, y + h, 0xFF111D28);
        g.drawString(font, font.plainSubstrByWidth(compact ? "3D BRAIN" : "MALE CNS · 3D CONNECTOME · CLICK TO OPEN",
                Math.max(1, routeW - 12)), routeX + 6, y + 5, 0xFFC8A8FF, true);
        connectomePreview.render(g, routeX + 6, y + 22, Math.max(1, routeW - 12), Math.max(1, h - 27), sample);
    }

    private void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (net.minecraft.client.gui.components.Renderable renderable : renderables)
            renderable.render(g, mouseX, mouseY, partialTick);
    }

    private List<TelemetryHistory.SeriesView> streams() {
        if (frozenStreams != null) return frozenStreams;
        List<TelemetryHistory.SeriesView> local = TelemetryHistory.snapshot();
        List<lab.neurolab.minecraft.BrainRosterPayload.Entry> roster = TelemetryRoster.snapshot();
        if (roster.isEmpty()) return local;
        java.util.Map<Integer, TelemetryHistory.SeriesView> byId = new java.util.HashMap<>();
        local.forEach(series -> byId.put(series.entityId(), series));
        return roster.stream().map(entry -> byId.get(entry.entityId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(TelemetryHistory.SeriesView::entityId)).toList();
    }

    private void toggleFreeze() {
        if (frozenStreams == null) {
            frozenStreams = List.copyOf(TelemetryHistory.snapshot());
            frozenActiveSamples = new java.util.HashMap<>();
            for (TelemetryHistory.SeriesView series : frozenStreams) {
                BrainTelemetryPayload active = TelemetryHistory.latest(series.entityId());
                if (active != null) frozenActiveSamples.put(series.entityId(), active);
            }
        } else {
            frozenStreams = null;
            frozenActiveSamples = null;
        }
        freezeButton.setMessage(Component.literal(frozenStreams == null ? "FREEZE" : "RESUME"));
    }

    private void viewSelectedMob() {
        if (selectedEntityId < 0) return;
        DashboardPreferences.enableViewOverlay();
        TelemetryNetwork.requestView(selectedEntityId);
        Minecraft.getInstance().setScreen(null);
    }

    private static Component hudToggleLabel() {
        String state = DashboardPreferences.showViewOverlay() ? "ON" : "OFF";
        return Component.literal("HUD " + state);
    }

    private Component viewLabel() {
        return Component.literal(headerLayout.compact() ? "VIEW" : "MOB VIEW");
    }

    private Component connectomeLabel() {
        return headerLayout.compact() ? Component.literal("CONNECT")
                : Component.translatable("screen.neurolab.connectome.button");
    }

    private Component guideLabel() {
        return headerLayout.compact() ? Component.literal("HELP")
                : Component.translatable("screen.neurolab.guide.short");
    }

    private Component detailLabel() {
        return Component.literal(detailMode ? "OVERVIEW" : "MOB DETAILS");
    }

    private static Component killLabel(boolean confirmation) {
        return Component.literal(confirmation ? "CONFIRM KILL?" : "KILL SUBJECT")
                .withStyle(confirmation ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED);
    }

    private static String controlLabel(BrainTelemetryPayload sample) {
        return !sample.mode().controlsBody() ? "OBSERVATION ONLY"
                : sample.reflex() ? "REFLEX LAYER" : "NEURAL OUTPUT";
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= subjectListX && x < subjectListX + subjectListW
                && y >= subjectListY && y < subjectListBottom
                && visibleStreams.size() > subjectWindow.visibleCount()) {
            subjectScroll = subjectWindow.clampFirstIndex(subjectScroll - (int) Math.signum(vertical));
            return true;
        }
        if (y >= chartViewportTop && y < chartViewportBottom && maxChartScroll > 0) {
            chartScroll = Math.max(0, Math.min(maxChartScroll, chartScroll - (int) (vertical * 36)));
            return true;
        }
        if (vertical != 0 && maxChartScroll > 0) {
            chartScroll = Math.max(0, Math.min(maxChartScroll, chartScroll - (int) (vertical * 36)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !detailMode && mouseX >= connectomePreviewX
                && mouseX < connectomePreviewX + connectomePreviewW
                && mouseY >= connectomePreviewY && mouseY < connectomePreviewY + connectomePreviewH) {
            Minecraft.getInstance().setScreen(new ConnectomeExplorerScreen(this, selectedEntityId));
            return true;
        }
        if (button == 0 && mouseX >= subjectListX && mouseX < subjectListX + subjectListW
                && mouseY >= subjectListY && mouseY < subjectListBottom && !visibleStreams.isEmpty()) {
            int index = subjectWindow.indexAt(mouseY, subjectListY, subjectScroll);
            if (index >= 0 && index < visibleStreams.size()) {
                selectedEntityId = visibleStreams.get(index).entityId();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawSidePanel(GuiGraphics g, int x, int y, int w, int h, String title,
                               String[][] rows, int accent) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        g.fill(x, y, x + 2, y + h, accent);
        g.drawString(font, title, x + 8, y + 8, accent, true);
        g.fill(x + 8, y + 23, x + w - 8, y + 24, 0xFF486174);
        int rowY = y + 34;
        for (String[] row : rows) {
            if (rowY + 20 >= y + h) break;
            g.drawString(font, font.plainSubstrByWidth(row[0], w - 16), x + 8, rowY,
                    0xFFAFBFCC, true);
            String value = font.plainSubstrByWidth(row[1], w - 16);
            g.drawString(font, value, x + 8, rowY + 10, 0xFFF2F7FA, true);
            rowY += 35;
        }
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private void drawChart(GuiGraphics g, int x, int y, int w, int h,
                           List<BrainTelemetryPayload> samples, Metric metric) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        BrainTelemetryPayload latest = samples.getLast();
        String value = metric.format(metric.value(latest));
        g.drawString(font, font.plainSubstrByWidth(metric.label,
                Math.max(1, w - font.width(value) - 24)), x + 7, y + 4, metric.color, true);
        g.drawString(font, value, x + w - font.width(value) - 7, y + 4, 0xFFFFFFFF, true);

        int plotLeft = x + 7;
        int plotRight = x + w - 7;
        int plotTop = y + 19;
        int plotBottom = y + h - 13;
        int plotW = plotRight - plotLeft;
        int plotH = plotBottom - plotTop;
        if (plotW <= 0 || plotH <= 1) return;

        var bins = TelemetryPlotBins.calculate(samples, plotW, sample -> metric.value(sample));
        float scale = metric.fixedRange;
        for (var bin : bins) {
            scale = Math.max(scale, (float) (metric.signed
                    ? Math.max(Math.abs(bin.minimum()), Math.abs(bin.maximum())) : bin.maximum()));
        }
        if (scale <= 0f) scale = 1f;

        for (int grid = 0; grid < 3; grid++) {
            int gy = plotTop + grid * plotH / 2;
            g.fill(plotLeft, gy, plotRight, gy + 1, 0x994C6072);
        }
        int baseline = metric.signed ? plotTop + plotH / 2 : plotBottom;
        if (metric.signed) g.fill(plotLeft, baseline, plotRight, baseline + 1, 0xFF8DA1B0);

        int columns = bins.length;
        for (int column = 0; column < columns; column++) {
            float minimum = (float) bins[column].minimum();
            float maximum = (float) bins[column].maximum();

            int left = plotLeft + (int) ((long) column * plotW / columns);
            int right = plotLeft + (int) ((long) (column + 1) * plotW / columns);
            int top;
            int bottom;
            if (metric.signed) {
                top = signedPlotY(maximum, scale, baseline, plotH);
                bottom = signedPlotY(minimum, scale, baseline, plotH);
            } else {
                top = unsignedPlotY(maximum, scale, plotBottom, plotH);
                bottom = unsignedPlotY(minimum, scale, plotBottom, plotH);
            }
            if (top > bottom) { int swap = top; top = bottom; bottom = swap; }
            g.fill(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom + 1), metric.color);
        }
        g.drawString(font, "n=" + samples.size(), plotLeft, y + h - 10, 0xFFC4D0DA, true);
        g.drawString(font, "oldest → newest", plotRight - font.width("oldest → newest"), y + h - 10,
                0xFFC4D0DA, true);
    }

    private static int unsignedPlotY(float value, float scale, int plotBottom, int plotHeight) {
        float normalized = Math.min(1f, Math.max(0f, value) / scale);
        return plotBottom - Math.round((plotHeight - 1) * normalized);
    }

    private static int signedPlotY(float value, float scale, int baseline, int plotHeight) {
        float normalized = Math.min(1f, Math.abs(value) / scale);
        int amplitude = Math.round((plotHeight / 2f - 1) * normalized);
        return value >= 0 ? baseline - amplitude : baseline + amplitude;
    }

    private void drawFooter(GuiGraphics g, int x, int y, int w, String text) {
        g.drawString(font, font.plainSubstrByWidth(text, w), x + 2, y, 0xFFD6E0E8, true);
    }

    private static int indexOf(List<TelemetryHistory.SeriesView> streams, int entityId) {
        for (int i = 0; i < streams.size(); i++) {
            if (streams.get(i).entityId() == entityId) return i;
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) { toggleFreeze(); return true; }
        if (keyCode == GLFW.GLFW_KEY_V && selectedEntityId >= 0) {
            viewSelectedMob();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            chartScroll = Math.max(0, Math.min(maxChartScroll, chartScroll + direction * 180));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            List<TelemetryHistory.SeriesView> streams = streams().stream()
                    .filter(s -> !s.samples().isEmpty())
                    .sorted(Comparator.comparingInt(TelemetryHistory.SeriesView::entityId)).toList();
            if (streams.size() > 1) {
                int current = Math.max(0, indexOf(streams, selectedEntityId));
                int direction = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
                selectedEntityId = streams.get(Math.floorMod(current + direction, streams.size())).entityId();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Metric {
        SPIKES("SPIKES / STEP", 0xFF43C6EF, 0, false),
        ACTIVE("ACTIVE STATE CELLS", 0xFFB78DF2, 0, false),
        FORWARD("FORWARD DRIVE", 0xFF72D69A, 1, false),
        TURN("TURN BIAS", 0xFFFFB75E, 1, true),
        LIFT("LIFT DRIVE", 0xFF58C9F3, 1, false),
        REALTIME("REAL-TIME FACTOR", 0xFFEE8497, 1, false),
        LIGHT("CENTER EYE LIGHT", 0xFF78D6F2, 1, false),
        LEFT_EYE("LEFT EYE LIGHT", 0xFF63B8E8, 1, false),
        RIGHT_EYE("RIGHT EYE LIGHT", 0xFF9C9AF2, 1, false),
        LOOMING("LOOMING", 0xFFFF9F7A, 1, false),
        TOUCH("TOUCH", 0xFFFFC16D, 1, false),
        ODOR("ODOR", 0xFFBAE07A, 1, false),
        TASTE("TASTE", 0xFFE79BD2, 1, false),
        BODY_FORWARD("BODY FORWARD", 0xFF72D69A, 1, false),
        BODY_TURN("BODY TURN", 0xFFFFB75E, 1, true),
        BODY_LIFT("BODY LIFT", 0xFF58C9F3, 1, false),
        SYNAPTIC_EVENTS("EDGE VISITS / STEP", 0xFFBAA4F2, 0, false),
        SOUND("SOUND / VIBRATION", 0xFFF0CF79, 1, false),
        PAIN("PAIN INPUT", 0xFFFF8D88, 1, false),
        REWARD("REWARD INPUT", 0xFF85E1A0, 1, false),
        SATIETY("HUNGER PROXY", 0xFFFFB75E, 1, false),
        HYDRATION("THIRST PROXY", 0xFF57C7E7, 1, false),
        MODIFIED_EDGES("MODIFIED EDGES", 0xFFC7A6F1, 0, false);

        private final String label;
        private final int color;
        private final float fixedRange;
        private final boolean signed;

        Metric(String label, int color, float fixedRange, boolean signed) {
            this.label = label;
            this.color = color;
            this.fixedRange = fixedRange;
            this.signed = signed;
        }

        private float value(BrainTelemetryPayload p) {
            return switch (this) {
                case SPIKES -> p.spikes();
                case ACTIVE -> p.active();
                case FORWARD -> p.forward();
                case TURN -> p.turn();
                case LIFT -> p.lift();
                case REALTIME -> p.realTimeFactor();
                case LIGHT -> p.light();
                case LEFT_EYE -> p.leftEye();
                case RIGHT_EYE -> p.rightEye();
                case LOOMING -> p.looming();
                case TOUCH -> p.touch();
                case ODOR -> p.odor();
                case TASTE -> p.taste();
                case BODY_FORWARD -> p.bodyForward();
                case BODY_TURN -> p.bodyTurn();
                case BODY_LIFT -> p.bodyLift();
                case SYNAPTIC_EVENTS -> p.synapticEvents();
                case SOUND -> p.sound();
                case PAIN -> p.pain();
                case REWARD -> p.reward();
                case SATIETY -> p.hunger();
                case HYDRATION -> p.thirst();
                case MODIFIED_EDGES -> p.learnedSynapseCount();
            };
        }

        private String format(float v) {
            return switch (this) {
                case SPIKES, ACTIVE, SYNAPTIC_EVENTS, MODIFIED_EDGES -> String.format(Locale.ROOT, "%.0f", v);
                case FORWARD, LIFT, LIGHT, LEFT_EYE, RIGHT_EYE, LOOMING, TOUCH, ODOR, TASTE,
                     BODY_FORWARD, BODY_LIFT, SOUND, PAIN, REWARD, SATIETY, HYDRATION ->
                        String.format(Locale.ROOT, "%.0f%%", v * 100);
                case TURN, BODY_TURN -> String.format(Locale.ROOT, "%+.2f", v);
                case REALTIME -> String.format(Locale.ROOT, "%.2fx", v);
            };
        }
    }
}
