package lab.neurolab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, persistent controls for dashboard panels and visible plots. */
public final class NeuroSettingsScreen extends Screen {
    private final Screen parent;
    private int chartPage;
    private Button previousPage;
    private Button nextPage;

    public NeuroSettingsScreen(Screen parent) {
        super(Component.literal("NeuroLab Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean compact = height < 360;
        int panelW = Math.min(720, Math.max(0, width - 32));
        int x = (width - panelW) / 2;
        int buttonW = (panelW - 12) / 2;
        int top = compact ? Math.min(86, height - 175) : Math.max(82, height / 2 - 116);
        addRenderableWidget(toggle("Left sensory panel", DashboardPreferences.showLeftPanel(),
                x, top, buttonW, DashboardPreferences::toggleLeftPanel));
        addRenderableWidget(toggle("Right motor panel", DashboardPreferences.showRightPanel(),
                x + buttonW + 12, top, buttonW, DashboardPreferences::toggleRightPanel));

        addRenderableWidget(Button.builder(hudLabel(), button -> {
                    DashboardPreferences.toggleViewOverlay();
                    button.setMessage(hudLabel());
                }).bounds(x, top + 26, buttonW, 20).build());
        addRenderableWidget(Button.builder(label("Brain hologram", DashboardPreferences.showBrainHologram()), b -> {
            DashboardPreferences.toggleBrainHologram();
            b.setMessage(label("Brain hologram", DashboardPreferences.showBrainHologram()));
        }).bounds(x + buttonW + 12, top + 26, buttonW, 20).build());

        int chartTop = top + (compact ? 54 : 60);
        int rowCount = compact ? 4 : 8;
        int pageSize = compact ? 8 : 16;
        chartPage = Math.min(chartPage, (DashboardPreferences.CHART_NAMES.length - 1) / pageSize);
        int visibleCount = Math.min(pageSize, DashboardPreferences.CHART_NAMES.length - chartPage * pageSize);
        int rowH = compact ? 20 : Math.min(22, Math.max(18, (height - chartTop - 66) / rowCount));
        int rowGap = compact ? 3 : 4;
        for (int i = 0; i < visibleCount; i++) {
            int chartIndex = chartPage * pageSize + i;
            int col = compact ? i % 2 : i / 8;
            int row = compact ? i / 2 : i % 8;
            int bx = x + col * (buttonW + 12);
            int by = chartTop + row * (rowH + rowGap);
            String name = DashboardPreferences.CHART_NAMES[chartIndex];
            addRenderableWidget(toggle(name, DashboardPreferences.showChart(chartIndex), bx, by,
                    buttonW, () -> DashboardPreferences.toggleChart(chartIndex)));
        }
        if (DashboardPreferences.CHART_NAMES.length > pageSize) {
            int footerY = height - 28;
            previousPage = addRenderableWidget(Button.builder(Component.literal("‹ PLOTS"), b -> changePage(-1))
                    .bounds(width / 2 - 112, footerY, 70, 20).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal("PLOTS ›"), b -> changePage(1))
                    .bounds(width / 2 + 42, footerY, 70, 20).build());
            updatePageButtons();
        }
        addRenderableWidget(Button.builder(Component.literal("DONE"), b -> onClose())
                .bounds(width / 2 - 35, height - 28, 70, 20).build());
    }

    private void changePage(int delta) {
        int pageSize = height < 360 ? 8 : 16;
        int pageCount = (DashboardPreferences.CHART_NAMES.length + pageSize - 1) / pageSize;
        chartPage = Math.floorMod(chartPage + delta, pageCount);
        rebuildWidgets();
    }

    private void updatePageButtons() {
        int pageSize = height < 360 ? 8 : 16;
        int pageCount = (DashboardPreferences.CHART_NAMES.length + pageSize - 1) / pageSize;
        String page = (chartPage + 1) + "/" + pageCount;
        if (previousPage != null) previousPage.setMessage(Component.literal("‹ " + page));
        if (nextPage != null) nextPage.setMessage(Component.literal(page + " ›"));
    }

    private Button toggle(String name, boolean enabled, int x, int y, int w, Runnable action) {
        Button button = Button.builder(label(name, enabled), b -> {
            action.run();
            boolean nowEnabled = name.equals("Left sensory panel") ? DashboardPreferences.showLeftPanel()
                    : name.equals("Right motor panel") ? DashboardPreferences.showRightPanel()
                    : DashboardPreferences.showChart(java.util.Arrays.asList(DashboardPreferences.CHART_NAMES).indexOf(name));
            b.setMessage(label(name, nowEnabled));
        }).bounds(x, y, w, 20).build();
        return button;
    }

    private static Component label(String name, boolean enabled) {
        return Component.literal((enabled ? "ON  ·  " : "OFF ·  ") + name);
    }

    private static Component hudLabel() {
        return Component.literal((DashboardPreferences.showViewOverlay() ? "ON  ·  " : "OFF ·  ")
                + "Neuro View HUD (I)");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF070B10);
        int panelW = Math.min(720, Math.max(0, width - 32));
        int x = (width - panelW) / 2;
        int y = 22;
        g.fill(x - 1, y - 1, x + panelW + 1, height - 22, 0xFF486174);
        g.fill(x, y, x + panelW, height - 23, 0xFF0B1118);
        g.fill(x, y, x + panelW, y + 2, 0xFF49BCE4);
        g.drawCenteredString(font, "NEUROLAB  /  DASHBOARD SETTINGS", width / 2, 34, 0xFFB9ECFF);
        if (height < 360) {
            g.drawCenteredString(font, "Display settings · flight behavior is configured in Forge Mods",
                    width / 2, 52, 0xFFD6E0E8);
        } else {
            g.drawCenteredString(font, "Plots and panels below affect this view. Flight and light behavior are server settings:",
                    width / 2, 56, 0xFFD6E0E8);
            g.drawCenteredString(font, "Mods  >  NeuroLab  >  Config  ·  light mode, cruise, flowers, host perching, rain shelter",
                    width / 2, 68, 0xFF8ACDE4);
        }
        for (net.minecraft.client.gui.components.Renderable renderable : renderables)
            renderable.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
