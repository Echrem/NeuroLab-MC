package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryPlotBinsTest {
    @Test
    void retainsTransientPeakWhenHistoryIsMuchWiderThanTheScreen() {
        List<Integer> samples = new ArrayList<>(java.util.Collections.nCopies(72_000, 0));
        samples.set(36_001, 140);
        var bins = TelemetryPlotBins.calculate(samples, 320, Integer::doubleValue);
        assertEquals(320, bins.length);
        assertEquals(140, java.util.Arrays.stream(bins).mapToDouble(TelemetryPlotBins.Bucket::maximum).max().orElseThrow());
        assertEquals(0, java.util.Arrays.stream(bins).mapToDouble(TelemetryPlotBins.Bucket::minimum).min().orElseThrow());
    }

    @Test
    void handlesEmptyAndNonFiniteValues() {
        assertEquals(0, TelemetryPlotBins.calculate(List.<Double>of(), 100, Double::doubleValue).length);
        var bins = TelemetryPlotBins.calculate(List.of(Double.NaN, 0.5), 1, Double::doubleValue);
        assertEquals(0.5, bins[0].minimum());
        assertEquals(0.5, bins[0].maximum());
    }
}
