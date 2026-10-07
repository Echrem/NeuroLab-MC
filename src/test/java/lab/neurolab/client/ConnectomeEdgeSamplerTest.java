package lab.neurolab.client;

import lab.neurolab.brain.ConnectomeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ConnectomeEdgeSamplerTest {
    @Test
    void keepsOnlyRealEdgesBetweenDisplayedPointsAndPreservesSignAndWeight() {
        int[] points = {0, 1, 2};
        int[] rows = {0, 2, 3, 3};
        int[] targets = {1, 2, 2};
        short[] weights = {4, 9, 3};
        byte[] signs = {1, -1, 0};

        var edges = ConnectomeEdgeSampler.sample(points, rows, targets, weights, signs, 3, 10, 2);

        assertEquals(3, edges.size());
        assertTrue(edges.contains(new ConnectomeEdgeSampler.Edge(0, 1, 4, (byte) 1)));
        assertTrue(edges.contains(new ConnectomeEdgeSampler.Edge(0, 2, 9, (byte) 1)));
        assertTrue(edges.contains(new ConnectomeEdgeSampler.Edge(1, 2, 3, (byte) -1)));
    }

    @Test void unknownTransmitterPolarityIsNotConvertedIntoExcitation() {
        var edges = ConnectomeEdgeSampler.sample(new int[]{0, 1}, new int[]{0, 1, 1},
                new int[]{1}, new short[]{5}, new byte[]{0, 1}, 2, 5, 2);
        assertEquals(1, edges.size());
        assertEquals(0, edges.getFirst().sign());
        assertFalse(edges.getFirst().inhibitory());
    }

    @Test
    void respectsSourceAndEdgeBudgetsDeterministically() {
        int[] points = {0, 1, 2, 3};
        int[] rows = {0, 3, 5, 7, 7};
        int[] targets = {1, 2, 3, 0, 2, 0, 1};
        short[] weights = {1, 2, 3, 4, 5, 6, 7};
        byte[] signs = {1, 1, -1, -1};

        var first = ConnectomeEdgeSampler.sample(points, rows, targets, weights, signs, 2, 3, 2);
        var second = ConnectomeEdgeSampler.sample(points, rows, targets, weights, signs, 2, 3, 2);

        assertEquals(first, second);
        assertTrue(first.size() <= 3);
        assertTrue(first.stream().allMatch(edge -> edge.sourcePoint() < 4 && edge.targetPoint() < 4));
    }

    @Test
    void bundledMaleCnsProducesVisibleAnatomicalLinksAtDefaultDetail() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        int positioned = 0;
        for (int neuron = 0; neuron < graph.neurons(); neuron++)
            if (graph.hasSomaPosition(neuron)) positioned++;
        int[] positionedIds = new int[positioned];
        for (int neuron = 0, at = 0; neuron < graph.neurons(); neuron++)
            if (graph.hasSomaPosition(neuron)) positionedIds[at++] = neuron;

        int count = Math.min(24_000, positionedIds.length);
        int[] points = new int[count];
        for (int point = 0; point < count; point++)
            points[point] = positionedIds[(int) ((long) point * positionedIds.length / count)];
        var edges = ConnectomeEdgeSampler.sample(points, graph.rowOffsets, graph.targets,
                graph.connectionWeights, graph.transmitterSigns, 200, 280, 2);

        assertFalse(edges.isEmpty());
        assertTrue(edges.size() <= 280);
        assertTrue(edges.stream().allMatch(edge -> edge.sourcePoint() < count && edge.targetPoint() < count));

        int[] previewPoints = new int[Math.min(480, positionedIds.length)];
        for (int point = 0; point < previewPoints.length; point++)
            previewPoints[point] = positionedIds[(int) ((long) point * positionedIds.length / previewPoints.length)];
        var previewEdges = ConnectomeEdgeSampler.sample(previewPoints, graph.rowOffsets, graph.targets,
                graph.connectionWeights, graph.transmitterSigns, previewPoints.length, 96, 2);
        assertTrue(previewEdges.size() >= 24,
                "Dashboard preview needs enough real anatomical links to be visually legible: " + previewEdges.size());
        assertTrue(previewEdges.size() <= 96);
    }
}
