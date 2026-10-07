package lab.neurolab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import lab.neurolab.brain.ConnectomeData;
import lab.neurolab.minecraft.BrainTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.RenderLivingEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Renders a compact, telemetry-lit hologram using bundled connectome soma coordinates and edges. */
final class NeuroBrainHologramRenderer {
    private static final int STRUCTURAL_POINTS = 480;
    private static final int EDGE_BUDGET = 128;
    private static final int ACTIVE_POINT_LIMIT = 144;
    private static final double VIEW_DISTANCE_SQUARED = 24 * 24;
    private static final CompletableFuture<Geometry> GEOMETRY = ConnectomeGraphCache.load()
            .thenApply(Geometry::new);
    private static final Map<Mob, Activity> ACTIVITY = new WeakHashMap<>();

    private NeuroBrainHologramRenderer() {}

    static void render(RenderLivingEvent.Post<?, ?> event) {
        if (!DashboardPreferences.showBrainHologram() || !(event.getEntity() instanceof Mob mob)) return;
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || camera.distanceToSqr(mob) > VIEW_DISTANCE_SQUARED
                || !GEOMETRY.isDone() || GEOMETRY.isCompletedExceptionally()) return;

        BrainTelemetryPayload sample = TelemetryHistory.latest(mob.getId());
        if (sample == null) return;
        Geometry geometry = GEOMETRY.getNow(null);
        if (geometry == null || geometry.points.length == 0) return;

        Activity activity = ACTIVITY.computeIfAbsent(mob, ignored -> new Activity());
        activity.update(sample, geometry);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        double pulse = 1.0 + Math.sin((mob.tickCount + event.getPartialTick()) * 0.18) * 0.035;
        poseStack.translate(0, mob.getBbHeight() + 0.52, 0);
        poseStack.mulPose(Axis.YP.rotation((mob.tickCount + event.getPartialTick()) * 0.012f));
        float scale = (float) (0.9 * pulse);
        poseStack.scale(scale, scale, scale);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());

        for (ConnectomeEdgeSampler.Edge edge : geometry.edges) {
            Point from = geometry.points[edge.sourcePoint()];
            Point to = geometry.points[edge.targetPoint()];
            int color = edge.sign() == 0 ? 0x807B8A98 : edge.inhibitory() ? 0xB0CE9AFF : 0xB05CC7E8;
            line(lines, pose, from.x, from.y, from.z, to.x, to.y, to.z, color);
        }
        for (Point point : geometry.points) {
            int color = point.color;
            cross(lines, pose, point.x, point.y, point.z, 0.014f, color);
        }
        float activeScale = 0.020f + Math.min(0.012f, sample.active() / 12000.0f);
        int activeColor = sample.pain() > sample.reward() * 1.4f && sample.pain() > 0.08f
                ? 0xFFFF7181 : sample.reward() > 0.08f ? 0xFF79FFB0 : 0xFFFFBA64;
        for (int neuron : activity.neurons) {
            if (neuron < 0 || neuron >= geometry.xyz.length / 3) continue;
            int offset = neuron * 3;
            cross(lines, pose, geometry.xyz[offset], geometry.xyz[offset + 1],
                    geometry.xyz[offset + 2], activeScale, activeColor);
        }
        poseStack.popPose();
    }

    private static void cross(VertexConsumer out, PoseStack.Pose pose,
                              float x, float y, float z, float radius, int color) {
        line(out, pose, x - radius, y, z, x + radius, y, z, color);
        line(out, pose, x, y - radius, z, x, y + radius, z, color);
        line(out, pose, x, y, z - radius, x, y, z + radius, color);
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        out.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx, ny, nz);
        out.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz);
    }

    private static final class Activity {
        private BrainTelemetryPayload sample;
        private int[] neurons = new int[0];

        private void update(BrainTelemetryPayload next, Geometry geometry) {
            if (sample == next) return;
            sample = next;
            int idCount = next.firingNeuronCount();
            int count = Math.min(ACTIVE_POINT_LIMIT, idCount);
            int[] selected = new int[count];
            int kept = 0;
            for (int i = 0; i < count; i++) {
                int neuron = next.firingNeuronIdAt((int) ((long) i * idCount / count));
                if (geometry.hasPosition(neuron)) selected[kept++] = neuron;
            }
            neurons = java.util.Arrays.copyOf(selected, kept);
        }
    }

    private static final class Geometry {
        private final Point[] points;
        private final float[] xyz;
        private final List<ConnectomeEdgeSampler.Edge> edges;

        private Geometry(ConnectomeData graph) {
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            int positioned = 0;
            for (int neuron = 0; neuron < graph.neurons(); neuron++) {
                if (!graph.hasSomaPosition(neuron)) continue;
                positioned++;
                minX = Math.min(minX, graph.somaX[neuron]); maxX = Math.max(maxX, graph.somaX[neuron]);
                minY = Math.min(minY, graph.somaY[neuron]); maxY = Math.max(maxY, graph.somaY[neuron]);
                minZ = Math.min(minZ, graph.somaZ[neuron]); maxZ = Math.max(maxZ, graph.somaZ[neuron]);
            }
            float span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            if (!Float.isFinite(span) || span <= 0) span = 1;
            xyz = new float[graph.neurons() * 3];
            java.util.Arrays.fill(xyz, Float.NaN);
            int[] positionedIds = new int[positioned];
            for (int neuron = 0, at = 0; neuron < graph.neurons(); neuron++) {
                if (!graph.hasSomaPosition(neuron)) continue;
                positionedIds[at++] = neuron;
                int index = neuron * 3;
                xyz[index] = (float) (ConnectomeProjection.centeredAxis(
                        graph.somaX[neuron], minX, maxX, span));
                xyz[index + 1] = (float) (ConnectomeProjection.centeredAxis(
                        graph.somaY[neuron], minY, maxY, span));
                xyz[index + 2] = (float) (ConnectomeProjection.centeredAxis(
                        graph.somaZ[neuron], minZ, maxZ, span));
            }
            int count = Math.min(STRUCTURAL_POINTS, positionedIds.length);
            int[] selected = new int[count];
            points = new Point[count];
            for (int point = 0; point < count; point++) {
                int neuron = positionedIds[(int) ((long) point * positionedIds.length / count)];
                selected[point] = neuron;
                int index = neuron * 3;
                points[point] = new Point(xyz[index], xyz[index + 1], xyz[index + 2], colorFor(graph, neuron));
            }
            edges = ConnectomeEdgeSampler.sample(selected, graph.rowOffsets, graph.targets,
                    graph.connectionWeights, graph.transmitterSigns, count, EDGE_BUDGET, 2);
        }

        private boolean hasPosition(int neuron) {
            int index = neuron * 3;
            return neuron >= 0 && index + 2 < xyz.length && Float.isFinite(xyz[index]);
        }

        private static int colorFor(ConnectomeData graph, int neuron) {
            String superclass = graph.neuronSuperclasses[neuron];
            if (superclass.contains("sensory") || superclass.contains("visual")) return 0xB5FFCA72;
            if (superclass.contains("descending") || superclass.contains("motor")) return 0xB577E4A2;
            if (graph.transmitterSigns[neuron] < 0) return 0xB5CE9AFF;
            return 0xB58AC8E0;
        }
    }

    private record Point(float x, float y, float z, int color) {}
}
