package lab.neurolab.client;

import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Small live 3D anatomical connectome view shared by the lab dashboard. */
final class ConnectomeBrainPreview {
    private static final int POINT_BUDGET = 1_600;
    private static final int EDGE_BUDGET = 240;
    private final CompletableFuture<ConnectomeData> load = ConnectomeGraphCache.load();

    private ConnectomeData graph;
    private int[] pointNeuronIds = new int[0];
    private List<ConnectomeEdgeSampler.Edge> edges = List.of();
    private boolean[] activeMask;
    private int[] previousActiveIds = new int[0];
    private BrainTelemetryPayload activeSample;
    private float minX, minY, minZ, maxX, maxY, maxZ, largestSpan;

    void render(GuiGraphics graphics, int x, int y, int width, int height, BrainTelemetryPayload sample) {
        if (width < 32 || height < 30) return;
        if (graph == null && load.isDone() && !load.isCompletedExceptionally()) initialize(load.join());
        if (graph == null) {
            String status = load.isCompletedExceptionally() ? "CONNECTOME LOAD FAILED" : "LOADING MALE CNS…";
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, status,
                    x + width / 2, y + height / 2 - 4, 0xFFFFC16D);
            return;
        }

        updateActivity(sample);
        graphics.fill(x, y, x + width, y + height, 0xFF0B121A);
        int plotBottom = y + height - 11;
        graphics.enableScissor(x, y, x + width, plotBottom);
        double yaw = System.nanoTime() * 0.00000000012;
        double pitch = 0.42;
        double scale = Math.max(1, Math.min(width, plotBottom - y) * 0.83);
        for (int point = 0; point < pointNeuronIds.length; point++) {
            int neuron = pointNeuronIds[point];
            float[] projected = project(neuron, x, y, width, plotBottom - y, scale, yaw, pitch);
            int screenX = Math.round(projected[0]);
            int screenY = Math.round(projected[1]);
            boolean active = activeMask[neuron];
            int radius = active ? 2 : 1;
            graphics.fill(screenX - radius, screenY - radius, screenX + radius + 1,
                    screenY + radius + 1, active ? 0xFFFF895D : colorFor(neuron));
        }
        for (ConnectomeEdgeSampler.Edge edge : edges) {
            float[] from = project(pointNeuronIds[edge.sourcePoint()], x, y, width, plotBottom - y,
                    scale, yaw, pitch);
            float[] to = project(pointNeuronIds[edge.targetPoint()], x, y, width, plotBottom - y,
                    scale, yaw, pitch);
            drawSparseLine(graphics, Math.round(from[0]), Math.round(from[1]), Math.round(to[0]),
                    Math.round(to[1]), edge.sign() == 0 ? 0x90788898
                            : edge.inhibitory() ? 0xD5AB76DC : 0xD54EBBCC);
        }
        for (int neuron : pointNeuronIds) {
            if (!activeMask[neuron]) continue;
            float[] projected = project(neuron, x, y, width, plotBottom - y, scale, yaw, pitch);
            int screenX = Math.round(projected[0]), screenY = Math.round(projected[1]);
            graphics.fill(screenX - 2, screenY - 2, screenX + 3, screenY + 3, 0xFFFF895D);
        }
        // Include reported firing cells even when they fall outside the fixed anatomy sample.
        int highlights = Math.min(256, previousActiveIds.length);
        for (int i = 0; i < highlights; i++) {
            int neuron = previousActiveIds[(int) ((long) i * previousActiveIds.length / highlights)];
            if (!graph.hasSomaPosition(neuron)) continue;
            float[] p = project(neuron, x, y, width, plotBottom - y, scale, yaw, pitch);
            int px = Math.round(p[0]), py = Math.round(p[1]);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFB578);
        }
        graphics.disableScissor();

        String status = String.format(Locale.ROOT, "%d sampled neurons · %d anatomical synapses · %d active IDs",
                pointNeuronIds.length, edges.size(), previousActiveIds.length);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                net.minecraft.client.Minecraft.getInstance().font.plainSubstrByWidth(status, width - 6),
                x + 3, y + height - 9, 0xFF9EB1BF, false);
    }

    private void initialize(ConnectomeData loaded) {
        graph = loaded;
        minX = minY = minZ = Float.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
        int positioned = 0;
        for (int neuron = 0; neuron < graph.neurons(); neuron++) {
            if (!graph.hasSomaPosition(neuron)) continue;
            positioned++;
            minX = Math.min(minX, graph.somaX[neuron]); maxX = Math.max(maxX, graph.somaX[neuron]);
            minY = Math.min(minY, graph.somaY[neuron]); maxY = Math.max(maxY, graph.somaY[neuron]);
            minZ = Math.min(minZ, graph.somaZ[neuron]); maxZ = Math.max(maxZ, graph.somaZ[neuron]);
        }
        largestSpan = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (!Float.isFinite(largestSpan) || largestSpan <= 0) largestSpan = 1;

        int[] positionedIds = new int[positioned];
        for (int neuron = 0, at = 0; neuron < graph.neurons(); neuron++)
            if (graph.hasSomaPosition(neuron)) positionedIds[at++] = neuron;
        int count = Math.min(POINT_BUDGET, positionedIds.length);
        pointNeuronIds = new int[count];
        for (int point = 0; point < count; point++)
            pointNeuronIds[point] = positionedIds[(int) ((long) point * positionedIds.length / count)];
        edges = ConnectomeEdgeSampler.sample(pointNeuronIds, graph.rowOffsets, graph.targets,
                graph.connectionWeights, graph.transmitterSigns, pointNeuronIds.length, EDGE_BUDGET, 2);
        activeMask = new boolean[graph.neurons()];
    }

    private void updateActivity(BrainTelemetryPayload sample) {
        if (sample == activeSample) return;
        for (int neuron : previousActiveIds)
            if (neuron >= 0 && neuron < activeMask.length) activeMask[neuron] = false;
        activeSample = sample;
        previousActiveIds = sample == null ? new int[0] : sample.firingNeuronIds();
        for (int neuron : previousActiveIds)
            if (neuron >= 0 && neuron < activeMask.length) activeMask[neuron] = true;
    }

    private float[] project(int neuron, int x, int y, int width, int height,
                            double scale, double yaw, double pitch) {
        double px = ConnectomeProjection.centeredAxis(graph.somaX[neuron], minX, maxX, largestSpan);
        double py = ConnectomeProjection.centeredAxis(graph.somaY[neuron], minY, maxY, largestSpan);
        double pz = ConnectomeProjection.centeredAxis(graph.somaZ[neuron], minZ, maxZ, largestSpan);
        double rx = px * Math.cos(yaw) - pz * Math.sin(yaw);
        double depth = px * Math.sin(yaw) + pz * Math.cos(yaw);
        double rotatedY = py * Math.cos(pitch) - depth * Math.sin(pitch);
        return new float[]{(float) (x + width / 2.0 + rx * scale),
                (float) (y + height / 2.0 + rotatedY * scale)};
    }

    private int colorFor(int neuron) {
        String superclass = graph.neuronSuperclasses[neuron];
        if (superclass.contains("sensory")) return 0xFFFFB866;
        if (superclass.contains("descending") || superclass.contains("motor")) return 0xFF78D89A;
        if (superclass.contains("visual")) return 0xFF64CBEA;
        if (graph.transmitterSigns[neuron] < 0) return 0xFFBF8DE5;
        return 0xFF7893A6;
    }

    private static void drawSparseLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        int stride = Math.max(1, steps / 18);
        for (int step = 0; step <= steps; step += stride) {
            int px = x0 + dx * step / steps;
            int py = y0 + dy * step / steps;
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }
}
