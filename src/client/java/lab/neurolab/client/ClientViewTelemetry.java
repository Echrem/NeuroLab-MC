package lab.neurolab.client;

import lab.neurolab.minecraft.BrainTelemetryPayload;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** A separate, bounded feed for the current camera subject, including entities outside normal tracking range. */
public final class ClientViewTelemetry {
    private static final long FRESH_NANOS = 1_500_000_000L;
    private static final int HISTORY_LIMIT = 120;
    private static final ConcurrentHashMap<Integer, Series> DATA = new ConcurrentHashMap<>();

    private ClientViewTelemetry() {}

    public static void accept(BrainTelemetryPayload payload) {
        DATA.computeIfAbsent(payload.entityId(), ignored -> new Series()).push(payload);
        long now = System.nanoTime();
        DATA.entrySet().removeIf(entry -> now - entry.getValue().updatedAt > 10_000_000_000L);
    }

    public static BrainTelemetryPayload latest(int entityId) {
        Series series = DATA.get(entityId);
        return series == null || System.nanoTime() - series.updatedAt > FRESH_NANOS ? null : series.latest;
    }

    public static List<BrainTelemetryPayload> history(int entityId) {
        Series series = DATA.get(entityId);
        return series == null ? List.of() : series.history();
    }

    public static void clear() { DATA.clear(); }

    private static final class Series {
        private final ArrayDeque<BrainTelemetryPayload> history = new ArrayDeque<>(HISTORY_LIMIT);
        private volatile long updatedAt = System.nanoTime();
        private volatile BrainTelemetryPayload latest;

        synchronized void push(BrainTelemetryPayload payload) {
            if (history.size() == HISTORY_LIMIT) history.removeFirst();
            history.addLast(payload.withoutFiringNeuronIds());
            latest = payload;
            updatedAt = System.nanoTime();
        }

        synchronized List<BrainTelemetryPayload> history() { return List.copyOf(history); }
    }
}
