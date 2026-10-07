package lab.neurolab.client;

import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import lab.neurolab.minecraft.SynapseInspectionPayload;
import lab.neurolab.minecraft.TelemetryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Interactive orthographic 3D projection of the bundled anatomical connectome. */
public final class ConnectomeExplorerScreen extends Screen {
    private static final String[] FILTERS = {"", "descending_neuron", "visual_projection", "ol_intrinsic",
            "cb_intrinsic", "cb_sensory", "vnc_intrinsic"};
    private static final int[] POINT_LIMITS = {3_000, 6_000, 12_000, 24_000, 48_000, 72_000};
    private static final int SYNAPSE_EDGE_BUDGET = 640;
    private final CompletableFuture<ConnectomeData> graphLoad = ConnectomeGraphCache.load();

    private ConnectomeData graph;
    private int filterIndex;
    private int densityIndex = 3;
    private int selected = -1;
    private int sampledFilter = -1, sampledDensity = -1;
    private boolean showSynapses = true;
    private boolean inspectGlobal = true;
    private boolean showAnatomy = true;
    private boolean compactDetails;
    private String hoveredAnnotation;
    private int detailMouseX, detailMouseY;
    private int anatomyScroll;
    private double zoom = 1;
    private int inspectionScroll;
    private int requestedNeuron = Integer.MIN_VALUE;
    private long nextInspectionRequest;
    private List<ConnectomeEdgeSampler.Edge> sampledEdges = List.of();
    private boolean[] activeNeuronMask;
    private int[] previousActiveIds = new int[0];
    private BrainTelemetryPayload activeSample;
    private final Screen parent;
    private final int subjectEntityId;
    private int[] visibleNeuronIds = new int[0];
    private double yaw = 0.55;
    private double pitch = 0.48;
    private boolean dragging;
    private boolean dragged;
    private double dragOriginX, dragOriginY;
    private int positionedNeurons;
    private int viewLeft, viewTop, viewRight, viewBottom;
    private ConnectomeViewport viewport;
    private float minX, minY, minZ, maxX, maxY, maxZ;
    private float largestSpan;
    private int[] projectedX = new int[0], projectedY = new int[0];
    private boolean[] projectedVisible = new boolean[0];
    private int projectionCount = -1, cachedLeft, cachedTop, cachedRight, cachedBottom;
    private double cachedYaw = Double.NaN, cachedPitch = Double.NaN;
    private double cachedZoom = Double.NaN;

    public ConnectomeExplorerScreen() {
        this(null, -1);
    }

    public ConnectomeExplorerScreen(Screen parent, int subjectEntityId) {
        super(Component.translatable("screen.neurolab.connectome.title"));
        this.parent = parent;
        this.subjectEntityId = subjectEntityId;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Do not let Minecraft's pause-menu blur show through this full-screen instrument.
        g.fill(0, 0, width, height, 0xFF080D13);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoveredAnnotation = null;
        detailMouseX = mouseX;
        detailMouseY = mouseY;
        viewport = ConnectomeViewport.calculate(width, height);
        g.fill(0, 0, width, height, 0xFF080D13);
        g.fill(12, 12, width - 12, height - 12, 0xFF17232E);
        g.fill(12, 12, width - 12, 14, 0xFF49BCE4);
        if (viewport.compact()) {
            g.drawString(font, "CONNECTOME · MALE CNS", 16, 17, 0xFFB9ECFF, true);
            drawButton(g, viewport.densityButtonX(), viewport.buttonY(), viewport.buttonWidth(),
                    Component.literal("DENSITY " + POINT_LIMITS[densityIndex] / 1_000 + "K"));
            drawButton(g, viewport.filterButtonX(), viewport.buttonY(), viewport.buttonWidth(),
                    Component.literal("FILTER " + compactFilterLabel()));
        } else {
            g.drawString(font, Component.translatable("screen.neurolab.connectome.title"), 24, 23, 0xFFB9ECFF, true);
            g.drawString(font, Component.translatable("screen.neurolab.connectome.instructions"), 24, 39,
                    0xFFB7C8D4, false);
            g.drawString(font, Component.translatable("screen.neurolab.connectome.legend"),
                    24, 52, 0xFF9EB1BF, false);
            drawButton(g, viewport.filterButtonX(), viewport.buttonY(), viewport.buttonWidth(),
                    Component.translatable("screen.neurolab.connectome.filter",
                            FILTERS[filterIndex].isEmpty() ? Component.translatable("screen.neurolab.connectome.all")
                                    : Component.literal(FILTERS[filterIndex])));
            drawButton(g, viewport.densityButtonX(), viewport.buttonY(), viewport.buttonWidth(),
                    Component.translatable("screen.neurolab.connectome.density",
                            String.format(Locale.ROOT, "%,d", POINT_LIMITS[densityIndex])));
        }
        viewLeft = viewport.left();
        viewTop = viewport.top();
        viewRight = viewport.right();
        viewBottom = viewport.bottom();
        g.fill(viewLeft, viewTop, viewRight, viewBottom, 0xFF0B121A);
        if (!viewport.compact()) g.fill(viewRight, viewTop, width - 24, viewBottom, 0xFF101A23);

        if (graph == null && graphLoad.isDone() && !graphLoad.isCompletedExceptionally()) {
            graph = graphLoad.join();
            findBounds();
            rebuildVisibleNeurons();
            selected = firstVisible();
            activeNeuronMask = new boolean[graph.neurons()];
        }
        if (graph == null) {
            g.drawCenteredString(font, Component.translatable(graphLoad.isCompletedExceptionally()
                            ? "screen.neurolab.connectome.failed" : "screen.neurolab.connectome.loading"),
                    (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2, 0xFFFFD48A);
        } else {
            requestInspection();
            updateProjectionCache();
            drawNetwork(g);
            if (viewport.compact()) {
                drawCompactSelected(g);
                if (compactDetails && selected >= 0) {
                    int left = Math.max(viewLeft, viewRight - 236);
                    g.fill(left, viewTop, viewRight, viewBottom, 0xF0101A23);
                    drawAnatomy(g, left + 8, viewTop + 8, viewRight - 8, viewBottom);
                }
            }
            else drawSelected(g);
        }
        g.drawString(font, viewport.compact()
                        ? "ESC close · drag/scroll · A details · F/D/S"
                        : "ESC close · drag rotate · wheel zoom · R reset · A anatomy/learning",
                viewport.compact() ? 16 : 24, viewport.compact() ? height - 17 : height - 31,
                0xFFD6E0E8, false);
        if (subjectEntityId >= 0) {
            BrainTelemetryPayload sample = TelemetryHistory.latest(subjectEntityId);
            String status = sample == null ? "SUBJECT #" + subjectEntityId + " · awaiting active-neuron samples"
                    : "SUBJECT #" + subjectEntityId + " · sampled IDs " + sample.firingNeuronCount()
                    + " · active cells " + sample.active();
            if (!viewport.compact()) g.drawString(font, font.plainSubstrByWidth(status, Math.max(0, width - 500)),
                    24, height - 45, 0xFFFFB58F, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredAnnotation != null) g.renderTooltip(font, Component.literal(hoveredAnnotation), mouseX, mouseY);
    }

    private void requestInspection() {
        if (subjectEntityId < 0) return;
        int neuron = inspectGlobal ? -1 : selected;
        if (neuron < 0 && !inspectGlobal) return;
        long now = System.nanoTime();
        if (neuron != requestedNeuron || now >= nextInspectionRequest) {
            TelemetryNetwork.requestSynapses(subjectEntityId, neuron);
            requestedNeuron = neuron;
            nextInspectionRequest = now + 1_000_000_000L;
        }
    }

    private void findBounds() {
        minX = minY = minZ = Float.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < graph.neurons(); i++) {
            if (!graph.hasSomaPosition(i)) continue;
            positionedNeurons++;
            minX = Math.min(minX, graph.somaX[i]); maxX = Math.max(maxX, graph.somaX[i]);
            minY = Math.min(minY, graph.somaY[i]); maxY = Math.max(maxY, graph.somaY[i]);
            minZ = Math.min(minZ, graph.somaZ[i]); maxZ = Math.max(maxZ, graph.somaZ[i]);
        }
        largestSpan = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (!Float.isFinite(largestSpan) || largestSpan <= 0) largestSpan = 0;
    }

    private void drawNetwork(GuiGraphics g) {
        g.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        ensureSynapseSample();
        updateActivitySample();
        int sampleLimit = sampleCount();
        int drawn = 0;
        for (int sample = 0; sample < sampleLimit; sample++) {
            if (!projectedVisible[sample]) continue;
            int id = sampledNeuron(sample, sampleLimit);
            int x = projectedX[sample], y = projectedY[sample];
            boolean active = activeNeuronMask != null && activeNeuronMask[id];
            int color = id == selected ? 0xFFFFFFFF : active ? 0xFFFF7D55 : colorFor(id);
            int dot = active || id == selected ? 4 : 2;
            g.fill(x - (active ? 1 : 0), y - (active ? 1 : 0), x + dot, y + dot, color);
            drawn++;
        }
        if (showSynapses) {
            drawSampledEdges(g, sampleLimit);
            drawLearnedEdges(g);
            if (selected >= 0) drawOutgoing(g, selected);
            for (int sample = 0; sample < sampleLimit; sample++) {
                int id = sampledNeuron(sample, sampleLimit);
                if (!projectedVisible[sample] || id != selected && !activeNeuronMask[id]) continue;
                int x = projectedX[sample], y = projectedY[sample];
                g.fill(x - 1, y - 1, x + 4, y + 4, id == selected ? 0xFFFFFFFF : 0xFFFF7D55);
            }
        }
        g.disableScissor();
        String summary = String.format(Locale.ROOT,
                "%s  ·  %d neurons  ·  %s %d anatomical synapses  ·  %d positioned",
                FILTERS[filterIndex].isEmpty() ? "ALL" : FILTERS[filterIndex], drawn,
                showSynapses ? "SHOWING" : "HIDDEN", showSynapses ? sampledEdges.size() : 0,
                positionedNeurons);
        g.drawString(font, font.plainSubstrByWidth(summary, Math.max(0, viewRight - viewLeft - 16)),
                viewLeft + 8, viewTop + 7,
                0xFFB7C8D4, false);
        if (visibleNeuronIds.length == 0) {
            g.drawCenteredString(font, Component.translatable("screen.neurolab.connectome.empty_filter"),
                    (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2, 0xFFFFD48A);
        }
    }

    private void drawLearnedEdges(GuiGraphics g) {
        SynapseInspectionPayload inspection = SynapseInspectionState.get(subjectEntityId, inspectGlobal ? -1 : selected);
        if (inspection == null) return;
        for (SynapseInspectionPayload.Edge edge : inspection.edges()) {
            if (!graph.hasSomaPosition(edge.source()) || !graph.hasSomaPosition(edge.target())) continue;
            float[] from = project(edge.source()), to = project(edge.target());
            int color = edge.change() >= 0 ? 0xFF77F0AA : 0xFFFF846E;
            drawSparseLine(g, Math.round(from[0]), Math.round(from[1]),
                    Math.round(to[0]), Math.round(to[1]), color);
            g.fill(Math.round(to[0]) - 2, Math.round(to[1]) - 2,
                    Math.round(to[0]) + 3, Math.round(to[1]) + 3, color);
        }
    }

    private void updateProjectionCache() {
        int count = sampleCount();
        if (projectionCount == count && cachedLeft == viewLeft && cachedTop == viewTop
                && cachedRight == viewRight && cachedBottom == viewBottom
                && Double.compare(cachedYaw, yaw) == 0 && Double.compare(cachedPitch, pitch) == 0
                && Double.compare(cachedZoom, zoom) == 0) return;
        projectedX = new int[count];
        projectedY = new int[count];
        projectedVisible = new boolean[count];
        for (int sample = 0; sample < count; sample++) {
            float[] point = project(sampledNeuron(sample, count));
            int x = Math.round(point[0]), y = Math.round(point[1]);
            projectedX[sample] = x;
            projectedY[sample] = y;
            projectedVisible[sample] = x >= viewLeft + 2 && x < viewRight - 2
                    && y >= viewTop + 2 && y < viewBottom - 2;
        }
        projectionCount = count;
        cachedLeft = viewLeft; cachedTop = viewTop; cachedRight = viewRight; cachedBottom = viewBottom;
        cachedYaw = yaw; cachedPitch = pitch;
        cachedZoom = zoom;
    }

    private void ensureSynapseSample() {
        if (sampledFilter == filterIndex && sampledDensity == densityIndex) return;
        int count = sampleCount();
        int[] pointIds = new int[count];
        for (int point = 0; point < count; point++) pointIds[point] = sampledNeuron(point, count);
        sampledEdges = ConnectomeEdgeSampler.sample(pointIds, graph.rowOffsets, graph.targets,
                graph.connectionWeights, graph.transmitterSigns, Math.min(400, count), SYNAPSE_EDGE_BUDGET, 2);
        sampledFilter = filterIndex;
        sampledDensity = densityIndex;
    }

    private void updateActivitySample() {
        BrainTelemetryPayload latest = subjectEntityId < 0 ? null : TelemetryHistory.latest(subjectEntityId);
        if (latest == activeSample) return;
        if (activeNeuronMask != null) {
            for (int id : previousActiveIds)
                if (id >= 0 && id < activeNeuronMask.length) activeNeuronMask[id] = false;
        }
        activeSample = latest;
        previousActiveIds = latest == null ? new int[0] : latest.firingNeuronIds();
        if (activeNeuronMask != null) {
            for (int id : previousActiveIds)
                if (id >= 0 && id < activeNeuronMask.length) activeNeuronMask[id] = true;
        }
    }

    private void drawSampledEdges(GuiGraphics g, int sampleLimit) {
        for (ConnectomeEdgeSampler.Edge edge : sampledEdges) {
            if (edge.sourcePoint() >= sampleLimit || edge.targetPoint() >= sampleLimit
                    || !projectedVisible[edge.sourcePoint()] || !projectedVisible[edge.targetPoint()]) continue;
            int color = edge.sign() == 0 ? 0x90788898 : edge.inhibitory() ? 0xD0A478D5 : 0xD043B7C8;
            drawSparseLine(g, projectedX[edge.sourcePoint()], projectedY[edge.sourcePoint()],
                    projectedX[edge.targetPoint()], projectedY[edge.targetPoint()], color);
        }
    }

    private void drawOutgoing(GuiGraphics g, int source) {
        float[] from = project(source);
        int start = graph.rowOffsets[source], degree = graph.rowOffsets[source + 1] - start;
        int edgeCount = Math.min(degree, 24);
        int color = graph.transmitterSigns[source] < 0 ? 0xFFE0A3FF : 0xFF6EEBFA;
        for (int sample = 0; sample < edgeCount; sample++) {
            int edge = start + (int) ((long) sample * degree / edgeCount);
            int target = graph.targets[edge];
            if (!graph.hasSomaPosition(target)) continue;
            float[] to = project(target);
            line(g, Math.round(from[0]), Math.round(from[1]), Math.round(to[0]), Math.round(to[1]), color);
        }
    }

    private void drawSelected(GuiGraphics g) {
        if (selected < 0) return;
        if (showAnatomy) { drawAnatomy(g, viewRight + 10, viewTop + 12, width - 24, viewBottom); return; }
        int x = viewRight + 10, y = viewTop + 12, available = width - x - 34;
        g.drawString(font, "SELECTED NEURON", x, y, 0xFF82D9F2, true);
        y += 18;
        g.drawString(font, font.plainSubstrByWidth("#" + selected + "  " + graph.neuronTypes[selected], available),
                x, y, 0xFFE4EDF3, false);
        y += 12;
        g.drawString(font, font.plainSubstrByWidth("BODY " + Long.toUnsignedString(graph.bodyIds[selected])
                + "  IN " + graph.incomingCounts[selected]
                + "  OUT " + (graph.rowOffsets[selected + 1] - graph.rowOffsets[selected]), available),
                x, y, 0xFFB7C8D4, false);
        y += 18;
        g.drawString(font, "ANATOMY · voxel coordinate", x, y, 0xFF82D9F2, true);
        y += 14;
        g.drawString(font, String.format(Locale.ROOT, "X %.0f  Y %.0f  Z %.0f",
                graph.somaX[selected], graph.somaY[selected], graph.somaZ[selected]), x, y, 0xFFE4EDF3, false);
        y += 18;
        g.drawString(font, inspectGlobal ? "LEARNED SYNAPSES · SUBJECT" : "LEARNED SYNAPSES · SELECTED", x, y,
                0xFF82D9F2, true);
        y += 15;
        SynapseInspectionPayload inspection = SynapseInspectionState.get(subjectEntityId, inspectGlobal ? -1 : selected);
        if (subjectEntityId < 0) {
            g.drawString(font, "Open from a monitored mob to inspect learned changes.", x, y, 0xFF9AAEBC, false);
        } else if (inspection == null) {
            g.drawString(font, "Awaiting subject's synapse snapshot...", x, y, 0xFF9AAEBC, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth("REV " + inspection.revision() + " · "
                    + inspection.learnedCount() + " changed edges", available), x, y, 0xFFD2DDE5, false);
            y += 14;
            g.drawString(font, font.plainSubstrByWidth(String.format(Locale.ROOT,
                    "CUES  flower %+.2f  water %+.2f  fire %+.2f",
                    inspection.flowerCue(), inspection.waterCue(), inspection.fireCue()),
                    available), x, y, 0xFFFFCE94, false);
            y += 15;
            g.drawString(font, font.plainSubstrByWidth(String.format(Locale.ROOT,
                    "SOCIAL  zombie %+.2f  villager %+.2f  blocked %d",
                    inspection.zombieCue(), inspection.villagerCue(), inspection.blockedCells()), available),
                    x, y, 0xFFFFCE94, false);
            y += 15;
            String playerCue = String.format(Locale.ROOT, "PLAYER THREAT  %d hits · cue %+.2f%s",
                    inspection.playerThreatHits(), inspection.playerCue(),
                    inspection.playerCue() < -0.12f ? " · avoidance learned" : " · forming");
            g.drawString(font, font.plainSubstrByWidth(playerCue, available), x, y, 0xFFFFCE94, false);
            y += 15;
            if (!inspection.strongestBlockCue().isBlank()) {
                String blockCue = String.format(Locale.ROOT, "BLOCK CUE  %s  %+.2f",
                        inspection.strongestBlockCue(), inspection.strongestBlockValue());
                g.drawString(font, font.plainSubstrByWidth(blockCue, available), x, y, 0xFFFFCE94, false);
                y += 15;
            }
            if (inspection.edges().isEmpty()) {
                g.drawString(font, "No rewarded pre/post pairs in this view yet.", x, y, 0xFF9AAEBC, false);
            }
            if (y < viewBottom - 35) {
                g.enableScissor(x, y, width - 24, viewBottom - 35);
                for (int rowIndex = inspectionScroll; rowIndex < inspection.edges().size(); rowIndex++) {
                    if (y + 10 >= viewBottom - 35) break;
                    SynapseInspectionPayload.Edge edge = inspection.edges().get(rowIndex);
                    String row = "#" + edge.source() + " > #" + edge.target()
                            + "  " + (edge.sign() < 0 ? "INH" : "EXC")
                            + "  " + edge.contacts() + " contacts  "
                            + String.format(Locale.ROOT, "%+.3f%%", edge.change() * 100);
                    g.drawString(font, font.plainSubstrByWidth(row, available), x, y,
                            edge.change() >= 0 ? 0xFF8BE5AD : 0xFFFFA698, false);
                    y += 12;
                }
                g.disableScissor();
            }
        }
        g.drawString(font, "G: global/selected · wheel: rows · green/red: learned change", x, viewBottom - 27,
                0xFF9AAEBC, false);
    }

    private void drawAnatomy(GuiGraphics g, int x, int top, int right, int bottom) {
        int available = right - x - 4;
        g.drawString(font, "NEURON ANATOMY", x, top, 0xFF82D9F2, true);
        String sign = graph.transmitterSigns[selected] == 0 ? "UNKNOWN"
                : graph.transmitterSigns[selected] < 0 ? "INHIBITORY" : "EXCITATORY";
        long contacts = 0;
        for (int edge = graph.rowOffsets[selected]; edge < graph.rowOffsets[selected + 1]; edge++)
            contacts += Short.toUnsignedInt(graph.connectionWeights[edge]);
        List<String> rows = List.of("INDEX #" + selected,
                "BODY " + Long.toUnsignedString(graph.bodyIds[selected]),
                "TYPE " + annotation(graph.neuronTypes[selected]),
                "SUPERCLASS " + annotation(graph.neuronSuperclasses[selected]),
                "CLASS " + annotation(graph.neuronClasses[selected]),
                "SUBCLASS " + annotation(graph.neuronSubclasses[selected]),
                "SIDE " + annotation(graph.neuronSides[selected]),
                "TRANSMITTER " + annotation(graph.transmitterLabels[selected]),
                "SIGN " + sign,
                "INPUT EDGES " + graph.incomingCounts[selected],
                "OUTPUT EDGES " + (graph.rowOffsets[selected + 1] - graph.rowOffsets[selected]),
                "OUTPUT CONTACTS " + contacts,
                String.format(Locale.ROOT, "SOMA %.0f / %.0f / %.0f", graph.somaX[selected],
                        graph.somaY[selected], graph.somaZ[selected]),
                activeSample == null ? "ACTIVITY: no live sample"
                        : activeNeuronMask[selected] ? "ACTIVITY: sampled firing" : "ACTIVITY: not in firing sample");
        anatomyScroll = Math.max(0, Math.min(rows.size() - 1, anatomyScroll));
        g.enableScissor(x, top + 16, right, bottom - 30);
        for (int row = anatomyScroll, y = top + 20; row < rows.size() && y < bottom - 38; row++, y += 13) {
            g.drawString(font, font.plainSubstrByWidth(rows.get(row), available), x, y, 0xFFD2DDE5, false);
            if (detailMouseX >= x && detailMouseX < right && detailMouseY >= y && detailMouseY < y + 12)
                hoveredAnnotation = rows.get(row);
        }
        g.disableScissor();
        g.drawString(font, viewport.compact() ? "A: close · wheel: details" : "A: learning · wheel: details",
                x, bottom - 27, 0xFF9AAEBC, false);
    }

    private static String annotation(String value) { return value.isBlank() ? "unclassified" : value; }

    private void drawCompactSelected(GuiGraphics g) {
        String selectedText = selected < 0 ? "Click a neuron to inspect it"
                : "#" + selected + " · " + graph.neuronTypes[selected];
        g.drawString(font, font.plainSubstrByWidth(selectedText, Math.max(1, width - 32)),
                16, viewBottom + 4, 0xFFD6E0E8, false);
    }

    private float[] project(int id) {
        double x = ConnectomeProjection.centeredAxis(graph.somaX[id], minX, maxX, largestSpan);
        double y = ConnectomeProjection.centeredAxis(graph.somaY[id], minY, maxY, largestSpan);
        double z = ConnectomeProjection.centeredAxis(graph.somaZ[id], minZ, maxZ, largestSpan);
        double rx = x * Math.cos(yaw) - z * Math.sin(yaw);
        double depth = x * Math.sin(yaw) + z * Math.cos(yaw);
        double py = y * Math.cos(pitch) - depth * Math.sin(pitch);
        double scale = Math.max(80, Math.min(viewRight - viewLeft, viewBottom - viewTop) * 0.82) * zoom;
        return new float[]{(float) ((viewLeft + viewRight) / 2.0 + rx * scale),
                (float) ((viewTop + viewBottom) / 2.0 + py * scale)};
    }

    private boolean visible(int id) {
        String filter = FILTERS[filterIndex];
        return filter.isEmpty() || filter.equals(graph.neuronSuperclasses[id]);
    }

    private void rebuildVisibleNeurons() {
        int count = 0;
        for (int i = 0; i < graph.neurons(); i++)
            if (visible(i) && graph.hasSomaPosition(i)) count++;
        visibleNeuronIds = new int[count];
        for (int i = 0, at = 0; i < graph.neurons(); i++)
            if (visible(i) && graph.hasSomaPosition(i)) visibleNeuronIds[at++] = i;
    }

    private int colorFor(int id) {
        String superclass = graph.neuronSuperclasses[id];
        if (superclass.contains("sensory")) return 0xFFFFB866;
        if (superclass.contains("descending") || superclass.contains("motor")) return 0xFF78D89A;
        if (superclass.contains("visual")) return 0xFF64CBEA;
        if (graph.transmitterSigns[id] < 0) return 0xFFBF8DE5;
        return 0xFF7893A6;
    }

    private int firstVisible() {
        return visibleNeuronIds.length == 0 ? -1 : visibleNeuronIds[0];
    }

    private int sampleCount() { return Math.min(visibleNeuronIds.length, POINT_LIMITS[densityIndex]); }

    private int sampledNeuron(int sample, int sampleCount) {
        return visibleNeuronIds[(int) ((long) sample * visibleNeuronIds.length / sampleCount)];
    }

    private void cycleFilter() {
        filterIndex = (filterIndex + 1) % FILTERS.length;
        rebuildVisibleNeurons();
        projectionCount = -1;
        if (selected < 0 || !visible(selected)) selected = firstVisible();
    }

    private String compactFilterLabel() {
        return switch (filterIndex) {
            case 1 -> "DESC";
            case 2 -> "VISUAL";
            case 3 -> "OLFACTORY";
            case 4 -> "CENTRAL";
            case 5 -> "SENSORY";
            case 6 -> "VNC";
            default -> "ALL";
        };
    }

    private static void drawButton(GuiGraphics g, int x, int y, int width, Component text) {
        g.fill(x, y, x + width, y + 20, 0xFF263845);
        g.drawCenteredString(Minecraft.getInstance().font, text, x + width / 2, y + 6, 0xFFE8F4FA);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static void drawSparseLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        int stride = Math.max(1, steps / 20);
        for (int step = 0; step <= steps; step += stride) {
            int x = x0 + dx * step / steps;
            int y = y0 + dy * step / steps;
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConnectomeViewport bounds = ConnectomeViewport.calculate(width, height);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.inDensityButton(mouseX, mouseY)) {
            densityIndex = (densityIndex + 1) % POINT_LIMITS.length;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bounds.inFilterButton(mouseX, mouseY)) {
            cycleFilter();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && graph != null && mouseX >= viewLeft && mouseX < viewRight
                && mouseY >= viewTop && mouseY < viewBottom) {
            dragging = true;
            dragged = false;
            dragOriginX = mouseX;
            dragOriginY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && !dragged && graph != null) selectNearest(dragOriginX, dragOriginY);
        dragging = false;
        dragged = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            dragged |= Math.abs(dragX) + Math.abs(dragY) > 0;
            yaw += dragX * 0.012;
            pitch = Math.max(-1.35, Math.min(1.35, pitch + dragY * 0.01));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void selectNearest(double mouseX, double mouseY) {
        int samples = sampleCount();
        double best = 100;
        for (int sample = 0; sample < samples; sample++) {
            if (!projectedVisible[sample]) continue;
            int id = sampledNeuron(sample, samples);
            float[] p = project(id);
            double distance = Math.hypot(p[0] - mouseX, p[1] - mouseY);
            if (distance < best) { selected = id; best = distance; }
        }
        if (best > 12) selected = -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (graph == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_A) {
            if (viewport != null && viewport.compact()) compactDetails = !compactDetails;
            else showAnatomy = !showAnatomy;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) { zoom = 1; yaw = 0.55; pitch = 0.48; return true; }
        if (keyCode == GLFW.GLFW_KEY_F) { cycleFilter(); anatomyScroll = 0; return true; }
        if (keyCode == GLFW.GLFW_KEY_D) { densityIndex = (densityIndex + 1) % POINT_LIMITS.length; return true; }
        if (keyCode == GLFW.GLFW_KEY_S) { showSynapses = !showSynapses; return true; }
        if (keyCode == GLFW.GLFW_KEY_G) {
            inspectGlobal = !inspectGlobal; inspectionScroll = 0; requestedNeuron = Integer.MIN_VALUE; return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) && graph != null) {
            int direction = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
            int index = java.util.Arrays.binarySearch(visibleNeuronIds, selected);
            index = index < 0 ? 0 : index;
            selected = visibleNeuronIds.length == 0 ? -1
                    : visibleNeuronIds[Math.floorMod(index + direction, visibleNeuronIds.length)];
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (viewport != null && viewport.compact() && compactDetails
                && mouseX >= Math.max(viewLeft, viewRight - 236) && mouseX < viewRight
                && mouseY >= viewTop && mouseY < viewBottom && vertical != 0) {
            anatomyScroll = Math.max(0, Math.min(13, anatomyScroll + (vertical < 0 ? 1 : -1)));
            return true;
        }
        if (viewport != null && !viewport.compact() && mouseX >= viewRight && vertical != 0) {
            if (showAnatomy) {
                anatomyScroll = Math.max(0, Math.min(13, anatomyScroll + (vertical < 0 ? 1 : -1)));
                return true;
            }
            SynapseInspectionPayload inspection = SynapseInspectionState.get(subjectEntityId,
                    inspectGlobal ? -1 : selected);
            if (inspection != null) inspectionScroll = Math.max(0,
                    Math.min(Math.max(0, inspection.edges().size() - 1), inspectionScroll + (vertical < 0 ? 1 : -1)));
            return true;
        }
        if (graph != null && mouseX >= viewLeft && mouseX < viewRight
                && mouseY >= viewTop && mouseY < viewBottom && vertical != 0) {
            zoom = Math.max(0.5, Math.min(3.0, zoom * Math.pow(1.12, vertical)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean isPauseScreen() { return false; }
}
