package lab.neurolab.client;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Pixel-width min/max envelope: preserves short transients without drawing every history sample. */
final class TelemetryPlotBins {
    private TelemetryPlotBins() {}

    static <T> Bucket[] calculate(List<T> samples, int pixelWidth, ToDoubleFunction<T> value) {
        int count = Math.min(samples.size(), Math.max(0, pixelWidth));
        Bucket[] result = new Bucket[count];
        for (int column = 0; column < count; column++) {
            int start = (int) ((long) column * samples.size() / count);
            int end = (int) ((long) (column + 1) * samples.size() / count);
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int index = start; index < end; index++) {
                double sample = value.applyAsDouble(samples.get(index));
                if (!Double.isFinite(sample)) continue;
                minimum = Math.min(minimum, sample);
                maximum = Math.max(maximum, sample);
            }
            result[column] = minimum == Double.POSITIVE_INFINITY
                    ? new Bucket(0, 0) : new Bucket(minimum, maximum);
        }
        return result;
    }

    record Bucket(double minimum, double maximum) {}
}
