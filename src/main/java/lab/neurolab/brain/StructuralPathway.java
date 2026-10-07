package lab.neurolab.brain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Shortest directed anatomical route from an annotated visual receptor to a decoded motor neuron. */
public final class StructuralPathway {
    private StructuralPathway() {}

    public record Step(long bodyId, String neuronType, int incomingSynapseWeight) {}
    public record Path(List<Step> steps) {
        public Path { steps = List.copyOf(steps); }
    }

    public static Optional<Path> shortestVisualToMotor(ConnectomeData graph) {
        int neuronCount = graph.neurons();
        if (graph.visualReceptors.length == 0 || graph.motorNeurons.length == 0) return Optional.empty();

        boolean[] motor = new boolean[neuronCount];
        for (int id : graph.motorNeurons) if (id >= 0 && id < neuronCount) motor[id] = true;
        int[] parent = new int[neuronCount];
        int[] parentEdge = new int[neuronCount];
        Arrays.fill(parent, -2);
        int[] queue = new int[neuronCount];
        int tail = 0;
        for (int id : graph.visualReceptors) {
            if (id >= 0 && id < neuronCount && parent[id] == -2) {
                parent[id] = -1;
                queue[tail++] = id;
            }
        }

        int target = -1;
        for (int head = 0; head < tail && target < 0; head++) {
            int from = queue[head];
            if (motor[from]) { target = from; break; }
            for (int edge = graph.rowOffsets[from]; edge < graph.rowOffsets[from + 1]; edge++) {
                int to = graph.targets[edge];
                if (parent[to] != -2) continue;
                parent[to] = from;
                parentEdge[to] = edge;
                if (motor[to]) { target = to; break; }
                queue[tail++] = to;
            }
        }
        if (target < 0) return Optional.empty();

        List<Step> reverse = new ArrayList<>();
        for (int node = target; node >= 0; node = parent[node]) {
            int edge = parentEdge[node];
            int weight = parent[node] < 0 || edge >= graph.connectionWeights.length ? 0
                    : Short.toUnsignedInt(graph.connectionWeights[edge]);
            reverse.add(new Step(graph.bodyIds[node], graph.neuronTypes[node], weight));
        }
        Collections.reverse(reverse);
        return Optional.of(new Path(reverse));
    }

    public static Path loadBundledShortestVisualToMotor() throws IOException {
        return shortestVisualToMotor(ConnectomeData.readBundled())
                .orElseThrow(() -> new IOException("No directed visual-receptor-to-motor path in bundled connectome"));
    }
}
