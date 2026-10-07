package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ConnectomeDataTest {
    @Test void repeatedStrongInputIsDeterministicAndKeepsTheActiveSetBounded() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        FlyBrain first = new FlyBrain(graph);
        FlyBrain second = new FlyBrain(graph);
        var input = new FlyBrain.Drive(1, 1, 1, 1, 1, 1, 1, 0, 0);
        for (int step = 0; step < 6; step++) {
            var snapshot = first.advance(input);
            assertEquals(snapshot, second.advance(input));
            assertTrue(snapshot.activeNeurons() > 256);
            assertTrue(snapshot.activeNeurons() <= graph.neurons());
            assertTrue(Double.isFinite(snapshot.forward()));
        }
    }
    @Test void rewardedSpikePairsModifyRealDatasetEdgesWithoutChangingTopology() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        FlyBrain brain = new FlyBrain(graph);
        int edgeCount = graph.connections();
        var rewarded = new FlyBrain.Drive(1, 1, 1, 1, 1, 1, 1, 0, 1);
        for (int step = 0; step < 12; step++) brain.advance(rewarded);
        assertTrue(brain.learnedSynapseCount() > 0, "Rewarded pre/post pairs should produce inspectable changes");
        assertEquals(edgeCount, graph.connections());
        for (long learned : brain.learnedSynapses()) {
            int edge = (int) (learned >>> 32);
            assertTrue(edge >= 0 && edge < edgeCount);
            assertTrue(brain.learnedSynapseCount() <= 8192);
        }
    }
    @Test void bundledDatasetHasExpectedDimensionsAndValidCsr() throws Exception {
        ConnectomeData data = ConnectomeData.readBundled();
        assertEquals("male-cns:v1.0", data.dataset);
        assertEquals(176_422, data.neurons());
        assertEquals(6_287_749, data.connections());
        assertEquals(data.neurons() + 1, data.rowOffsets.length);
        assertEquals(data.connections(), data.rowOffsets[data.neurons()]);
        assertEquals(9_619, data.visualReceptors.length);
        assertEquals(data.visualReceptors.length,
                data.visualLeftReceptors.length + data.visualRightReceptors.length);
        assertTrue(data.visualLeftReceptors.length > 0);
        assertTrue(data.visualRightReceptors.length > 0);
        for (byte side : data.visualSides) assertTrue(side == 1 || side == 3);
        assertTrue(data.motorNeurons.length > 0);
        assertTrue(data.olfactoryNeurons.length > 0);
        assertTrue(data.gustatoryNeurons.length > 0);
        assertTrue(data.tactileNeurons.length > 0);
        ConnectomeData.validateCsr(data.neurons(), data.connections(), data.rowOffsets);
        ConnectomeData.validateTargets(data.neurons(), data.targets);
        assertEquals(data.neurons(), data.graphStats.neuronsWithOutputs() + data.graphStats.isolatedNeurons());
        assertTrue(data.graphStats.maximumOutDegree() > 0);
        assertEquals(90_296_905L, data.graphStats.totalSynapseWeight());
        assertTrue(data.graphStats.meanOutDegree(data.neurons()) > 0);
        assertTrue(data.graphStats.inhibitoryNeurons() > 0);
        assertEquals(java.util.stream.IntStream.range(0, data.neurons())
                .filter(i -> data.transmitterSigns[i] == 0).count(), data.graphStats.unclassifiedSignNeurons());
        assertEquals(data.neurons(), data.bodyIds.length);
        assertEquals(data.neurons(), data.neuronSuperclasses.length);
        assertEquals(data.neurons(), data.neuronClasses.length);
        assertEquals(data.neurons(), data.neuronSubclasses.length);
        assertEquals(data.neurons(), data.transmitterLabels.length);
        assertEquals(data.neurons(), data.neuronSides.length);
        assertEquals(data.neurons(), data.somaX.length);
        assertEquals(data.connections(), java.util.Arrays.stream(data.incomingCounts).sum());
        assertEquals(141_781, java.util.stream.IntStream.range(0, data.neurons())
                .filter(data::hasSomaPosition).count());
        assertTrue(data.bodyIds[0] > 0);
        assertTrue(java.util.Arrays.stream(data.neuronSuperclasses).anyMatch("descending_neuron"::equals));
        assertTrue(java.util.Arrays.stream(data.transmitterLabels).anyMatch("acetylcholine"::equals));
        assertTrue(data.describeNeuron(0).contains("body "));
        assertTrue(data.describeNeuron(data.neurons()).startsWith("Neuron index outside"));
        ConnectomeData.IncomingIndex reverse = data.incomingIndex();
        assertEquals(data.connections(), reverse.edges().length);
        assertEquals(data.connections(), reverse.sources().length);
        assertEquals(data.connections(), reverse.offsets()[data.neurons()]);
        for (int target = 0; target < data.neurons(); target += 997) {
            for (int position = reverse.offsets()[target]; position < reverse.offsets()[target + 1]; position++) {
                int edge = reverse.edges()[position], source = reverse.sources()[position];
                assertEquals(target, data.targets[edge]);
                assertTrue(edge >= data.rowOffsets[source] && edge < data.rowOffsets[source + 1]);
            }
        }
    }

    @Test void visualToMotorPathIsAnActualDirectedPathInTheBundledConnectome() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        var path = StructuralPathway.shortestVisualToMotor(graph).orElseThrow();
        assertTrue(path.steps().size() >= 2);
        long firstId = path.steps().getFirst().bodyId();
        long lastId = path.steps().getLast().bodyId();
        int first = indexOf(graph.bodyIds, firstId);
        int last = indexOf(graph.bodyIds, lastId);
        assertTrue(java.util.Arrays.stream(graph.visualReceptors).anyMatch(id -> id == first));
        assertTrue(java.util.Arrays.stream(graph.motorNeurons).anyMatch(id -> id == last));
        for (int i = 1; i < path.steps().size(); i++) {
            int from = indexOf(graph.bodyIds, path.steps().get(i - 1).bodyId());
            int to = indexOf(graph.bodyIds, path.steps().get(i).bodyId());
            boolean edgeExists = false;
            for (int edge = graph.rowOffsets[from]; edge < graph.rowOffsets[from + 1]; edge++) {
                if (graph.targets[edge] == to) { edgeExists = true; break; }
            }
            assertTrue(edgeExists, "Path step must be an existing directed connectome edge");
        }
    }

    private static int indexOf(long[] ids, long bodyId) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == bodyId) return i;
        return -1;
    }

    @Test void malformedCsrAndOutOfRangeTargetsAreRejected() {
        assertThrows(java.io.IOException.class, () -> ConnectomeData.validateCsr(2, 1, new int[]{1, 1, 1}));
        assertThrows(java.io.IOException.class, () -> ConnectomeData.validateCsr(2, 1, new int[]{0, 2, 1}));
        assertThrows(java.io.IOException.class, () -> ConnectomeData.validateCsr(2, 1, new int[]{0, 0, 0}));
        assertThrows(java.io.IOException.class, () -> ConnectomeData.validateTargets(2, new int[]{0, 2}));
        assertThrows(java.io.IOException.class, () -> ConnectomeData.validateTargets(2, new int[]{-1}));
    }

    @Test void silentNetworkHasNoSpontaneousSpikes() throws Exception {
        FlyBrain brain = new FlyBrain(ConnectomeData.readBundled());
        for (int tick = 0; tick < 4; tick++) {
            FlyBrain.Snapshot snapshot = brain.advance(new FlyBrain.Drive(0, 0, 0, 0, 0));
            assertEquals(0, snapshot.spikes());
            assertEquals(0, snapshot.synapticEvents());
            assertEquals(0, snapshot.forward());
            assertFalse(snapshot.escape());
        }
    }

    @Test void unknownTransmitterPolarityDoesNotGetSilentlyTreatedAsExcitatory() {
        assertEquals(0.0, FlyBrain.signedSynapticInput((short) 12, (byte) 0, 1.0));
        assertTrue(FlyBrain.signedSynapticInput((short) 12, (byte) 1, 1.0) > 0);
        assertTrue(FlyBrain.signedSynapticInput((short) 12, (byte) -1, 1.0) < 0);
    }

    @Test void visualInputEntersThroughTheConnectomesRetinaNeurons() throws Exception {
        FlyBrain brain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain.Snapshot snapshot = brain.advance(new FlyBrain.Drive(1, 0, 0, 0, 0));
        assertTrue(snapshot.spikes() > 0);
        assertTrue(snapshot.synapticEvents() > 0);
        assertTrue(snapshot.activeNeurons() > 0);
    }

    @Test void separateEyeDrivesReachTheSideAnnotatedRetina() throws Exception {
        FlyBrain leftBrain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain rightBrain = new FlyBrain(ConnectomeData.readBundled());
        FlyBrain.Snapshot left = leftBrain.advance(new FlyBrain.Drive(0, 1, 0, 0, 0, 0, 0));
        FlyBrain.Snapshot right = rightBrain.advance(new FlyBrain.Drive(0, 0, 1, 0, 0, 0, 0));
        assertTrue(left.spikes() > 0);
        assertTrue(right.spikes() > 0);
    }

    @Test void painAndRewardDriveTheirAnnotatedSensoryPopulations() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        FlyBrain painBrain = new FlyBrain(graph);
        FlyBrain rewardBrain = new FlyBrain(graph);
        var pain = painBrain.advance(new FlyBrain.Drive(0, 0, 0, 0, 0, 0, 0, 1, 0));
        var reward = rewardBrain.advance(new FlyBrain.Drive(0, 0, 0, 0, 0, 0, 0, 0, 1));

        assertTrue(pain.spikes() > 0);
        assertTrue(pain.synapticEvents() > 0);
        assertTrue(reward.spikes() > 0);
        assertTrue(reward.synapticEvents() > 0);
    }

    @Test void copiedMindImprintIsAppliedBeforeTheNextNeuralStep() throws Exception {
        ConnectomeData graph = ConnectomeData.readBundled();
        long[] imprint = { (42L << 32) | (Float.floatToIntBits(0.2f) & 0xffffffffL) };
        FlyBrain brain = new FlyBrain(graph);
        brain.copyLearnedSynapses(imprint);

        brain.advance(new FlyBrain.Drive(0, 0, 0, 0, 0));

        assertArrayEquals(imprint, brain.learnedSynapses());
    }
}
