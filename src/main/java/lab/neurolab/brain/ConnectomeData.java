package lab.neurolab.brain;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Minimal reader for the documented FLYB v1 interchange layout; this implementation is authored for NeuroLab. */
public final class ConnectomeData {
    public final String dataset;
    public final long[] bodyIds;
    public final String[] neuronTypes;
    public final String[] neuronSuperclasses, neuronClasses, neuronSubclasses, transmitterLabels, neuronSides;
    public final byte[] transmitterSigns;
    public final float[] somaX, somaY, somaZ;
    public final int[] olfactoryNeurons, gustatoryNeurons, tactileNeurons;
    public final int[] rowOffsets;
    public final int[] targets;
    public final short[] connectionWeights;
    public final int[] visualReceptors, visualLeftReceptors, visualRightReceptors;
    public final byte[] visualSides;
    public final int[] motorNeurons;
    public final int[] incomingCounts;
    private volatile IncomingIndex incomingIndex;
    public final GraphStats graphStats;

    public record GraphStats(int neuronsWithOutputs, int isolatedNeurons, int maximumOutDegree,
                             long totalSynapseWeight, int inhibitoryNeurons, int unclassifiedSignNeurons,
                             int totalEdges) {
        public double meanOutDegree(int neuronCount) {
            return neuronCount == 0 ? 0 : (double) totalEdges / neuronCount;
        }
    }

    private ConnectomeData(String dataset, long[] bodyIds, String[] types, String[] superclasses,
                           String[] classes, String[] subclasses, String[] transmitters, String[] sides, float[] somaX,
                           float[] somaY, float[] somaZ, byte[] signs, int[] olfactory, int[] gustatory,
                           int[] tactile, int[] rows, int[] targets, short[] weights, int[] visual,
                           byte[] visualSides, int[] visualLeft, int[] visualRight, int[] motors) {
        this.dataset = dataset;
        this.bodyIds = bodyIds;
        this.neuronTypes = types;
        this.neuronSuperclasses = superclasses;
        this.neuronClasses = classes;
        this.neuronSubclasses = subclasses;
        this.transmitterLabels = transmitters;
        this.neuronSides = sides;
        this.somaX = somaX;
        this.somaY = somaY;
        this.somaZ = somaZ;
        this.transmitterSigns = signs;
        this.olfactoryNeurons = olfactory;
        this.gustatoryNeurons = gustatory;
        this.tactileNeurons = tactile;
        this.rowOffsets = rows;
        this.targets = targets;
        this.connectionWeights = weights;
        this.visualReceptors = visual;
        this.visualSides = visualSides;
        this.visualLeftReceptors = visualLeft;
        this.visualRightReceptors = visualRight;
        this.motorNeurons = motors;
        this.incomingCounts = new int[types.length];
        for (int target : targets) incomingCounts[target]++;
        this.graphStats = summarize(rows, weights, signs);
    }

    public int neurons() { return neuronTypes.length; }
    public int connections() { return targets.length; }

    /** Reverse CSR is built once per shared graph, not once per attached mob. */
    public IncomingIndex incomingIndex() {
        IncomingIndex index = incomingIndex;
        if (index != null) return index;
        synchronized (this) {
            if (incomingIndex == null) {
                int[] offsets = new int[neurons() + 1];
                for (int neuron = 0; neuron < neurons(); neuron++)
                    offsets[neuron + 1] = offsets[neuron] + incomingCounts[neuron];
                int[] cursor = java.util.Arrays.copyOf(offsets, neurons());
                int[] edges = new int[connections()];
                int[] sources = new int[connections()];
                for (int source = 0; source < neurons(); source++) {
                    for (int edge = rowOffsets[source]; edge < rowOffsets[source + 1]; edge++) {
                        int position = cursor[targets[edge]]++;
                        edges[position] = edge;
                        sources[position] = source;
                    }
                }
                incomingIndex = new IncomingIndex(offsets, edges, sources);
            }
            return incomingIndex;
        }
    }

    public record IncomingIndex(int[] offsets, int[] edges, int[] sources) {}

    public static ConnectomeData readBundled() throws IOException {
        InputStream raw = ConnectomeData.class.getResourceAsStream("/connectome/male-cns-v1.0.flyb.gz");
        if (raw == null) throw new IOException("Bundled male-cns connectome not found");
        try (LEInput in = new LEInput(new BufferedInputStream(new GZIPInputStream(raw)))) {
            String magic = new String(in.bytes(4), StandardCharsets.US_ASCII);
            if (!"FLYB".equals(magic)) throw new IOException("Not a FLYB connectome: " + magic);
            int version = in.i32();
            if (version != 1) throw new IOException("Unsupported FLYB version: " + version);
            int n = in.i32(), m = in.i32(), retinaCount = in.i32();
            if (n < 1 || n > 2_000_000 || m < 1 || m > 100_000_000 || retinaCount < 0 || retinaCount > n * 4)
                throw new IOException("Implausible connectome dimensions: neurons=" + n + " edges=" + m);
            String dataset = in.str16();
            in.skipFully(in.i32()); // descriptive metadata JSON
            String[] typesTable = in.table16();
            String[] superclassTable = in.table16();
            String[] classTable = in.table16();
            String[][] labelTables = new String[7][];
            for (int i = 0; i < labelTables.length; i++) labelTables[i] = in.table16();

            long[] bodyIds = new long[n];
            for (int i = 0; i < n; i++) bodyIds[i] = in.i64();
            int[] typeIds = new int[n];
            for (int i = 0; i < n; i++) typeIds[i] = in.i32();
            byte[] superclassIds = in.bytes(n);
            byte[] classIds = in.bytes(n);
            int[] subclassIds = new int[n];
            for (int i = 0; i < n; i++) subclassIds[i] = in.u16();
            byte[] transmitterIds = in.bytes(n);
            byte[] signs = in.bytes(n);
            byte[] sideIds = in.bytes(n);
            in.skipFully((long) n * 2); // hexagonal anatomical axes
            in.skipFully(n); // dimorphism label
            in.skipFully(n); // sex marker label
            in.skipFully(n); // neuromere label
            in.skipFully(n); // nerve label
            float[] somaX = new float[n], somaY = new float[n], somaZ = new float[n];
            for (int i = 0; i < n; i++) {
                somaX[i] = in.f32();
                somaY[i] = in.f32();
                somaZ[i] = in.f32();
            }
            in.skipFully((long) n * 8); // additional per-neuron spatial measurements not used by this view

            int[] rows = new int[n + 1];
            for (int i = 0; i <= n; i++) rows[i] = in.i32();
            validateCsr(n, m, rows);
            int[] targets = new int[m];
            for (int i = 0; i < m; i++) targets[i] = in.i32();
            short[] weights = new short[m];
            for (int i = 0; i < m; i++) weights[i] = (short) in.u16();
            validateTargets(n, targets);

            int[] visualTmp = new int[retinaCount];
            byte[] visualSideTmp = new byte[retinaCount];
            int leftCount = 0, rightCount = 0;
            for (int i = 0; i < retinaCount; i++) {
                visualTmp[i] = in.i32();
                visualSideTmp[i] = (byte) in.u8();
                in.skipFully(3); // hex1, hex2, receptor kind
                if (visualSideTmp[i] == 1) leftCount++;
                else if (visualSideTmp[i] == 3) rightCount++;
            }

            String[] neuronTypes = new String[n];
            String[] neuronSuperclasses = new String[n], neuronClasses = new String[n], neuronSubclasses = new String[n];
            String[] transmitterLabels = new String[n], neuronSides = new String[n];
            int motorN = 0, odorN = 0, tasteN = 0, touchN = 0;
            for (int i = 0; i < n; i++) {
                int ti = typeIds[i];
                neuronTypes[i] = ti >= 0 && ti < typesTable.length ? typesTable[ti] : "";
                neuronSuperclasses[i] = label(superclassTable, superclassIds[i]);
                neuronClasses[i] = label(classTable, classIds[i]);
                neuronSubclasses[i] = label(labelTables[0], subclassIds[i]);
                transmitterLabels[i] = label(labelTables[1], transmitterIds[i]);
                neuronSides[i] = label(labelTables[2], sideIds[i]);
                if (neuronTypes[i].startsWith("MN") || neuronTypes[i].startsWith("DN")) motorN++;
                String cl = neuronClasses[i];
                if (neuronTypes[i].startsWith("ORN_") || "olfactory".equals(cl)) odorN++;
                if ("gustatory".equals(cl)) tasteN++;
                if (isTactile(cl)) touchN++;
            }
            int[] motors = new int[motorN], odor = new int[odorN], taste = new int[tasteN], touch = new int[touchN];
            for (int i = 0, mi = 0, oi = 0, gi = 0, xi = 0; i < n; i++) {
                String type = neuronTypes[i];
                if (type.startsWith("MN") || type.startsWith("DN")) motors[mi++] = i;
                String cl = neuronClasses[i];
                if (type.startsWith("ORN_") || "olfactory".equals(cl)) odor[oi++] = i;
                if ("gustatory".equals(cl)) taste[gi++] = i;
                if (isTactile(cl)) touch[xi++] = i;
            }
            int[] visualLeft = new int[leftCount], visualRight = new int[rightCount];
            for (int i = 0, li = 0, ri = 0; i < retinaCount; i++) {
                if (visualSideTmp[i] == 1) visualLeft[li++] = visualTmp[i];
                else if (visualSideTmp[i] == 3) visualRight[ri++] = visualTmp[i];
            }
            for (int id : visualTmp) if (id < 0 || id >= n) throw new IOException("Retina neuron index outside connectome");
            return new ConnectomeData(dataset, bodyIds, neuronTypes, neuronSuperclasses, neuronClasses, neuronSubclasses,
                    transmitterLabels, neuronSides, somaX, somaY, somaZ, signs, odor, taste, touch, rows, targets, weights,
                    visualTmp, visualSideTmp, visualLeft, visualRight, motors);
        }
    }

    static void validateCsr(int neurons, int edges, int[] rows) throws IOException {
        if (rows.length != neurons + 1) throw new IOException("CSR row count does not match neuron count");
        if (rows[0] != 0) throw new IOException("CSR offsets must start at zero");
        int previous = 0;
        for (int i = 1; i < rows.length; i++) {
            int current = rows[i];
            if (current < previous || current > edges)
                throw new IOException("Invalid CSR offset at neuron " + (i - 1));
            previous = current;
        }
        if (previous != edges) throw new IOException("CSR edge count does not match header");
    }

    static void validateTargets(int neurons, int[] targets) throws IOException {
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] < 0 || targets[i] >= neurons)
                throw new IOException("Connection target outside connectome at edge " + i);
        }
    }

    private static GraphStats summarize(int[] rows, short[] weights, byte[] signs) {
        int active = 0, maxOut = 0, inhibitory = 0, unclassified = 0;
        long totalWeight = 0;
        for (short weight : weights) totalWeight += Short.toUnsignedInt(weight);
        for (int neuron = 0; neuron < signs.length; neuron++) {
            int degree = rows[neuron + 1] - rows[neuron];
            if (degree > 0) active++;
            maxOut = Math.max(maxOut, degree);
            if (signs[neuron] < 0) inhibitory++;
            else if (signs[neuron] == 0) unclassified++;
        }
        return new GraphStats(active, signs.length - active, maxOut, totalWeight, inhibitory, unclassified,
                weights.length);
    }

    private static final class LEInput extends InputStream implements AutoCloseable {
        private final InputStream source;
        LEInput(InputStream source) { this.source = source; }
        int u8() throws IOException { int b = source.read(); if (b < 0) throw new EOFException(); return b; }
        int u16() throws IOException { return u8() | (u8() << 8); }
        int i32() throws IOException { return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24); }
        long i64() throws IOException { return Integer.toUnsignedLong(i32()) | (Integer.toUnsignedLong(i32()) << 32); }
        float f32() throws IOException { return Float.intBitsToFloat(i32()); }
        String str16() throws IOException { return new String(bytes(u16()), StandardCharsets.UTF_8); }
        String[] table16() throws IOException { String[] a = new String[u16()]; for (int i = 0; i < a.length; i++) a[i] = str16(); return a; }
        byte[] bytes(int n) throws IOException { byte[] b = source.readNBytes(n); if (b.length != n) throw new EOFException(); return b; }
        void skipFully(long n) throws IOException { while (n > 0) { long k = source.skip(n); if (k == 0) { if (source.read() < 0) throw new EOFException(); k = 1; } n -= k; } }
        @Override public int read() throws IOException { return source.read(); }
        @Override public void close() throws IOException { source.close(); }
    }

    private static String label(String[] table, byte id) {
        int index = Byte.toUnsignedInt(id);
        return index < table.length ? table[index] : "";
    }

    private static String label(String[] table, int id) {
        return id >= 0 && id < table.length ? table[id] : "";
    }

    public boolean hasSomaPosition(int neuron) {
        return neuron >= 0 && neuron < neurons() && Float.isFinite(somaX[neuron])
                && Float.isFinite(somaY[neuron]) && Float.isFinite(somaZ[neuron])
                && (somaX[neuron] != 0 || somaY[neuron] != 0 || somaZ[neuron] != 0);
    }

    public String describeNeuron(int neuron) {
        if (neuron < 0 || neuron >= neurons()) return "Neuron index outside dataset (0-" + (neurons() - 1) + ")";
        int out = rowOffsets[neuron + 1] - rowOffsets[neuron];
        return "#" + neuron + " · body " + Long.toUnsignedString(bodyIds[neuron])
                + " · " + nonempty(neuronTypes[neuron]) + " · class " + nonempty(neuronClasses[neuron])
                + " · subclass " + nonempty(neuronSubclasses[neuron])
                + " · superclass " + nonempty(neuronSuperclasses[neuron])
                + " · transmitter " + nonempty(transmitterLabels[neuron])
                + " · connectome sign " + (transmitterSigns[neuron] == 0 ? "unknown"
                        : transmitterSigns[neuron] < 0 ? "-1" : "+1")
                + " · side " + nonempty(neuronSides[neuron]) + " · in " + incomingCounts[neuron] + " · out " + out;
    }

    private static String nonempty(String value) { return value.isEmpty() ? "unknown" : value; }

    private static boolean isTactile(String neuronClass) {
        return neuronClass.startsWith("mechanosensory") || neuronClass.contains("proprioceptive");
    }
}
