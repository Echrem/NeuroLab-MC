package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Small bounded client-side ring history for the dashboard plots. */
public final class TelemetryHistory {
    public static final int LIMIT = 72_000;
    private static final long STALE_AFTER_NANOS = 10_000_000_000L;
    private static final ConcurrentHashMap<Integer, Series> DATA = new ConcurrentHashMap<>();
    private static volatile String currentDimension;
    private TelemetryHistory() {}
    public static void accept(BrainTelemetryPayload packet) {
        accept(packet, null);
    }

    public static void accept(BrainTelemetryPayload packet, String entityIdentity) {
        DATA.compute(packet.entityId(), (id, series) -> {
            if (series == null || (entityIdentity != null && !entityIdentity.equals(series.entityIdentity())))
                series = new Series(entityIdentity);
            series.push(packet);
            return series;
        });
        prune();
    }
    public static List<SeriesView> snapshot() {
        prune();
        List<SeriesView> result = new ArrayList<>();
        DATA.forEach((id, series) -> result.add(series.view(id)));
        return result;
    }
    public static BrainTelemetryPayload latest(int entityId) {
        Series series = DATA.get(entityId);
        if (series == null || System.nanoTime() - series.lastUpdatedNanos() > 1_500_000_000L) return null;
        return series.latest();
    }
    public static void clear() {
        DATA.clear();
        currentDimension = null;
    }

    /** Entity IDs are dimension-local and can be reused; never merge timelines across dimensions. */
    public static boolean changeDimension(String dimension) {
        if (dimension == null) return false;
        String previous = currentDimension;
        if (previous == null) {
            currentDimension = dimension;
            return false;
        }
        if (previous.equals(dimension)) return false;
        DATA.clear();
        currentDimension = dimension;
        return true;
    }

    private static void prune() {
        long now = System.nanoTime();
        DATA.entrySet().removeIf(entry -> now - entry.getValue().lastUpdatedNanos() > STALE_AFTER_NANOS);
    }

    private static final class Series {
        private final String entityIdentity;
        private final ArrayDeque<BrainTelemetryPayload> samples = new ArrayDeque<>(Math.min(LIMIT, 720));
        private volatile long updatedAt = System.nanoTime();
        private volatile BrainTelemetryPayload latest;
        private long revision;
        private SeriesView cachedView;
        Series(String entityIdentity) { this.entityIdentity = entityIdentity; }
        String entityIdentity() { return entityIdentity; }
        synchronized void push(BrainTelemetryPayload p) {
            if (samples.size() == LIMIT) samples.removeFirst();
            samples.addLast(p.withoutFiringNeuronIds());
            latest = p;
            updatedAt = System.nanoTime();
            revision++;
            cachedView = null;
        }
        long lastUpdatedNanos() { return updatedAt; }
        BrainTelemetryPayload latest() { return latest; }
        synchronized SeriesView view(int id) {
            if (cachedView == null) cachedView = new SeriesView(id, List.copyOf(samples), revision);
            return cachedView;
        }
    }
    public record SeriesView(int entityId, List<BrainTelemetryPayload> samples, long revision) {}
}
