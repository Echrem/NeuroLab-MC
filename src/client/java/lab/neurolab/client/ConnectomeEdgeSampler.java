package lab.neurolab.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds a deterministic, bounded set of real directed graph edges between displayed neurons. */
final class ConnectomeEdgeSampler {
    private static final int MAX_EDGE_INSPECTIONS_PER_SOURCE = 256;

    record Edge(int sourcePoint, int targetPoint, int weight, byte sign) {
        boolean inhibitory() { return sign < 0; }
    }

    private ConnectomeEdgeSampler() {}

    static List<Edge> sample(int[] pointNeuronIds, int[] rowOffsets, int[] targets, short[] weights,
                             byte[] transmitterSigns, int sourceBudget, int edgeLimit, int edgesPerSource) {
        if (pointNeuronIds.length == 0 || edgeLimit <= 0 || sourceBudget <= 0 || edgesPerSource <= 0
                || rowOffsets.length < 2 || targets.length != weights.length
                || transmitterSigns.length != rowOffsets.length - 1) return List.of();

        int[] pointByNeuron = new int[rowOffsets.length - 1];
        Arrays.fill(pointByNeuron, -1);
        for (int point = 0; point < pointNeuronIds.length; point++) {
            int neuron = pointNeuronIds[point];
            if (neuron >= 0 && neuron < pointByNeuron.length) pointByNeuron[neuron] = point;
        }

        int sourceCount = Math.min(sourceBudget, pointNeuronIds.length);
        List<Edge> result = new ArrayList<>(Math.min(edgeLimit, sourceCount * edgesPerSource));
        for (int sourceSlot = 0; sourceSlot < sourceCount && result.size() < edgeLimit; sourceSlot++) {
            int sourcePoint = (int) ((long) sourceSlot * pointNeuronIds.length / sourceCount);
            int source = pointNeuronIds[sourcePoint];
            if (source < 0 || source + 1 >= rowOffsets.length) continue;
            int start = rowOffsets[source];
            int degree = rowOffsets[source + 1] - start;
            int inspections = Math.min(degree, MAX_EDGE_INSPECTIONS_PER_SOURCE);
            int found = 0;
            for (int candidate = 0; candidate < inspections && found < edgesPerSource
                    && result.size() < edgeLimit; candidate++) {
                int offset = degree <= inspections ? candidate
                        : (int) ((long) candidate * degree / inspections);
                int edgeIndex = start + offset;
                int target = targets[edgeIndex];
                int targetPoint = target >= 0 && target < pointByNeuron.length ? pointByNeuron[target] : -1;
                if (targetPoint < 0 || targetPoint == sourcePoint) continue;
                result.add(new Edge(sourcePoint, targetPoint, Short.toUnsignedInt(weights[edgeIndex]),
                        transmitterSigns[source]));
                found++;
            }
        }
        return List.copyOf(result);
    }
}
