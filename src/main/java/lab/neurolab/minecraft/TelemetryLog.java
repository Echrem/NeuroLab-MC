package lab.neurolab.minecraft;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import lab.neurolab.brain.ControlMode;
import lab.neurolab.brain.TrialSession;
import net.minecraft.world.entity.Mob;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Non-blocking JSONL telemetry writer; neural observations never perform disk IO on a game tick. */
public final class TelemetryLog {
    private static final Logger LOG = Logger.getLogger("neurolab");
    private static volatile Session session;

    public record Status(boolean running, int queued, long dropped, boolean failed) {}

    private TelemetryLog() {}

    private record PendingSample(BrainTelemetryPayload payload, java.util.UUID entityUuid,
                                 String dimension, long gameTick, TrialSession.Snapshot trial,
                                 long neuralTick, double x, double y, double z) {}

    public static void start(Path path) { start(path, "unknown", "male-cns:v1.0"); }

    public static synchronized void start(Path path, String modVersion, String dataset) {
        if (session != null && session.writer.isAlive())
            throw new IllegalStateException("The previous telemetry writer has not stopped");
        session = new Session(path.toAbsolutePath(), modVersion, dataset);
        session.writer.start();
    }

    public static Status status() {
        Session current = session;
        return current == null ? new Status(false, 0, 0, false)
                : new Status(current.accepting, current.queue.size(), current.dropped.sum(), current.failed);
    }

    public static void record(BrainTelemetryPayload p, java.util.UUID entityUuid, String dimension, long gameTick,
                              TrialSession.Snapshot trial) {
        record(p, entityUuid, dimension, gameTick, trial, -1);
    }

    public static void record(BrainTelemetryPayload p, java.util.UUID entityUuid, String dimension, long gameTick,
                              TrialSession.Snapshot trial, long neuralTick) {
        record(p, entityUuid, dimension, gameTick, trial, neuralTick,
                Double.NaN, Double.NaN, Double.NaN);
    }

    public static void record(BrainTelemetryPayload p, java.util.UUID entityUuid, String dimension, long gameTick,
                              TrialSession.Snapshot trial, long neuralTick, double x, double y, double z) {
        Session current = session;
        if (current == null || !current.accepting) return;
        current.offer(new PendingSample(p, entityUuid, dimension, gameTick, trial, neuralTick, x, y, z));
    }

    public static void trialEvent(String event, Mob mob, ControlMode mode, TrialSession.Snapshot trial, long gameTick) {
        Session current = session;
        if (current == null || !current.accepting) return;
        String line = "{\"schemaVersion\":6,\"runId\":\"" + current.runId
                + "\",\"time\":\"" + Instant.now() + "\",\"event\":\"" + event
                + "\",\"entityId\":" + mob.getId() + ",\"entityUuid\":\"" + mob.getUUID()
                + "\",\"dimension\":\"" + mob.level().dimension().location()
                + "\",\"mode\":\"" + mode.id() + "\",\"trialLabel\":\""
                + trial.label() + "\",\"trialStartedAt\":" + trial.startedAtTick()
                + ",\"trialEndsAt\":" + trial.endsAtTick() + ",\"gameTick\":" + gameTick + "}";
        current.offer(line);
    }

    public static synchronized void stop() {
        Session current = session;
        if (current == null) return;
        current.close();
        try { current.writer.join(2000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (current.writer.isAlive()) LOG.warning("NeuroLab telemetry is still draining; a new writer cannot start yet");
        if (current.dropped.sum() > 0) LOG.warning("NeuroLab dropped " + current.dropped.sum() + " telemetry records");
    }

    public static void stimulusEvent(Mob mob, ControlMode mode, String sense, float strength, int durationTicks) {
        Session current = session;
        if (current == null || !current.accepting) return;
        var line = current.event("stimulus");
        line.addProperty("entityUuid", mob.getUUID().toString());
        line.addProperty("entityId", mob.getId());
        line.addProperty("dimension", mob.level().dimension().location().toString());
        line.addProperty("gameTick", mob.level().getGameTime());
        line.addProperty("mode", mode.id());
        line.addProperty("sense", sense);
        line.addProperty("strength", strength);
        line.addProperty("durationTicks", durationTicks);
        current.offer(line.toString());
    }

    /** Session-owned queues prevent stale observations from leaking between worlds. */
    private static final class Session {
        private final Path file;
        private final String runId = java.util.UUID.randomUUID().toString();
        private final String modVersion, dataset;
        private final ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(128);
        private final LongAdder dropped = new LongAdder();
        private final java.util.Map<String, Position> lastPositions = new java.util.HashMap<>();
        private volatile boolean accepting = true;
        private volatile boolean failed;
        private final Thread writer;

        private Session(Path file, String modVersion, String dataset) {
            this.file = file;
            this.modVersion = modVersion;
            this.dataset = dataset;
            writer = new Thread(this::writeLoop, "neurolab-telemetry-writer");
            writer.setDaemon(true);
        }

        private synchronized void offer(Object record) {
            if (accepting && !queue.offer(record)) dropped.increment();
        }

        private synchronized void close() { accepting = false; }

        private com.google.gson.JsonObject event(String name) {
            var line = new com.google.gson.JsonObject();
            line.addProperty("schemaVersion", 6);
            line.addProperty("runId", runId);
            line.addProperty("time", Instant.now().toString());
            line.addProperty("event", name);
            return line;
        }

        private void writeLoop() {
            try {
                Files.createDirectories(file.getParent());
                try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    var header = event("session_start");
                    header.addProperty("modVersion", modVersion);
                    header.addProperty("dataset", dataset);
                    header.addProperty("neuralStepMs", 50);
                    header.addProperty("sampleEveryGameTicks", NeuroFlyConfig.TELEMETRY_INTERVAL_TICKS.get());
                    header.addProperty("maxFiringNeuronIdsPerSample", NeuroFlyConfig.ACTIVE_NEURON_SAMPLE_LIMIT.get());
                    write(out, header.toString());
                    out.flush();
                    long lastFlush = System.nanoTime();
                    long written = 0;
                    while (accepting || !queue.isEmpty()) {
                        Object record = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (record != null) {
                            write(out, record instanceof PendingSample sample ? sampleJson(sample) : (String) record);
                            written++;
                        }
                        if (System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(1)) {
                            out.flush();
                            lastFlush = System.nanoTime();
                        }
                    }
                    var footer = event("session_end");
                    footer.addProperty("writtenRecords", written);
                    footer.addProperty("droppedRecords", dropped.sum());
                    write(out, footer.toString());
                }
            } catch (IOException e) {
                failed = true;
                LOG.log(Level.SEVERE, "Could not write NeuroLab event log " + file, e);
            } catch (InterruptedException e) {
                failed = true;
                Thread.currentThread().interrupt();
            } finally {
                close();
                queue.clear();
            }
        }

        private static void write(BufferedWriter out, String line) throws IOException {
            out.write(line);
            out.newLine();
        }

        private String sampleJson(PendingSample sample) {
            BrainTelemetryPayload p = sample.payload();
            TrialSession.Snapshot trial = sample.trial();
            String trialFields = trial == null ? ",\"trialLabel\":null,\"trialTick\":null"
                    : ",\"trialLabel\":\"" + trial.label() + "\",\"trialTick\":"
                    + trial.elapsedTicks(sample.gameTick()) + ",\"trialStartedAt\":" + trial.startedAtTick();
            Position previous = null;
            String positionKey = sample.entityUuid() + "@" + sample.dimension();
            String condition = p.mode().id() + ":" + (trial == null ? "none"
                    : trial.label() + "@" + trial.startedAtTick());
            if (Double.isFinite(sample.x()) && Double.isFinite(sample.y()) && Double.isFinite(sample.z())) {
                previous = lastPositions.put(positionKey,
                        new Position(sample.gameTick(), sample.x(), sample.y(), sample.z(), condition));
            } else lastPositions.remove(positionKey);
            String positionFields = Double.isFinite(sample.x()) && Double.isFinite(sample.y())
                    && Double.isFinite(sample.z()) ? ",\"x\":" + sample.x() + ",\"y\":" + sample.y()
                    + ",\"z\":" + sample.z() : ",\"x\":null,\"y\":null,\"z\":null";
            boolean deltaValid = previous != null && sample.gameTick() > previous.tick()
                    && sample.gameTick() - previous.tick() <= 200 && condition.equals(previous.condition());
            String deltaFields = deltaValid ? ",\"deltaX\":" + (sample.x() - previous.x())
                    + ",\"deltaY\":" + (sample.y() - previous.y()) + ",\"deltaZ\":"
                    + (sample.z() - previous.z()) + ",\"positionDeltaTicks\":"
                    + (sample.gameTick() - previous.tick())
                    : ",\"deltaX\":null,\"deltaY\":null,\"deltaZ\":null,\"positionDeltaTicks\":null";
            String prefix = "{\"schemaVersion\":6,\"runId\":\"" + runId
                    + "\",\"event\":\"sample\",\"neuralTick\":"
                    + (sample.neuralTick() < 0 ? "null" : sample.neuralTick())
                    + ",\"time\":\"" + Instant.now() + "\",\"entityId\":" + p.entityId()
                    + ",\"entityUuid\":\"" + sample.entityUuid() + "\",\"dimension\":\"" + sample.dimension()
                    + "\",\"gameTick\":" + sample.gameTick() + ",\"mode\":\"" + p.mode().id() + "\"" + trialFields
                    + positionFields + ",\"positionDeltaValid\":" + deltaValid + deltaFields
                    + ",\"spikes\":" + p.spikes() + ",\"synapticEvents\":" + p.synapticEvents()
                    + ",\"activeNeurons\":" + p.active()
                    + ",\"forward\":" + p.forward() + ",\"turn\":" + p.turn()
                    + ",\"lift\":" + p.lift() + ",\"realTimeFactor\":" + p.realTimeFactor()
                    + ",\"escape\":" + p.escape() + ",\"light\":" + p.light()
                    + ",\"leftEye\":" + p.leftEye() + ",\"rightEye\":" + p.rightEye()
                    + ",\"looming\":" + p.looming() + ",\"touch\":" + p.touch()
                    + ",\"odor\":" + p.odor() + ",\"taste\":" + p.taste()
                    + ",\"pain\":" + p.pain() + ",\"reward\":" + p.reward()
                    + ",\"satiety\":" + p.satiety() + ",\"hydration\":" + p.hydration()
                    + ",\"learnedSynapseCount\":" + p.learnedSynapseCount()
                    + ",\"learningRevision\":" + p.learningRevision()
                    + ",\"sound\":" + p.sound()
                    + ",\"bodyForward\":" + p.bodyForward() + ",\"bodyTurn\":" + p.bodyTurn()
                    + ",\"bodyLift\":" + p.bodyLift() + ",\"bodyEscape\":" + p.bodyEscape()
                    + ",\"reflex\":" + p.reflex() + ",\"testStimulus\":" + p.testStimulus()
                    + ",\"firingNeuronIds\":[";
            StringBuilder line = new StringBuilder(prefix.length() + p.firingNeuronCount() * 7 + 2)
                    .append(prefix);
            p.appendFiringNeuronIds(line);
            return line.append("]}").toString();
        }

        private record Position(long tick, double x, double y, double z, String condition) {}
    }
}
