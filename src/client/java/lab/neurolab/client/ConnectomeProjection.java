package lab.neurolab.client;

/** Shared-axis normalization keeps the anatomical aspect ratio in the 3D viewer. */
final class ConnectomeProjection {
    private ConnectomeProjection() {}

    static double centeredAxis(float value, float minimum, float maximum, float largestSpan) {
        if (!Float.isFinite(value) || !Float.isFinite(minimum) || !Float.isFinite(maximum)
                || !Float.isFinite(largestSpan) || largestSpan <= 0) return 0;
        return (value - ((double) minimum + maximum) * 0.5) / largestSpan;
    }
}
