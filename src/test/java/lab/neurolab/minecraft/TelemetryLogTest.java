package lab.neurolab.minecraft;

import com.google.gson.JsonParser;
import lab.neurolab.brain.TrialSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryLogTest {
    @TempDir Path temp;

    @Test void logRecordsStableIdentityAndExperimentalCondition() throws Exception {
        Path file = temp.resolve("events.jsonl");
        UUID uuid = UUID.randomUUID();
        TelemetryLog.start(file);
        try {
            TelemetryLog.record(new BrainTelemetryPayload(7, 12, 0L, 3, 0, 0, 0, 1, false,
                            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false,
                            lab.neurolab.brain.ControlMode.ASSISTED, 0.55f, new int[]{17, 42}),
                    uuid, "minecraft:overworld", 1234, null);
        } finally { TelemetryLog.stop(); }
        var lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        var header = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        var record = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assertEquals("session_start", header.get("event").getAsString());
        assertEquals(header.get("runId"), record.get("runId"));
        assertEquals(6, record.get("schemaVersion").getAsInt());
        assertEquals(java.util.List.of(17, 42), record.getAsJsonArray("firingNeuronIds").asList().stream()
                .map(com.google.gson.JsonElement::getAsInt).toList());
        assertEquals(0.55, record.get("sound").getAsDouble());
        assertEquals(0, record.get("synapticEvents").getAsLong());
        assertEquals(uuid.toString(), record.get("entityUuid").getAsString());
        assertEquals("minecraft:overworld", record.get("dimension").getAsString());
        assertEquals("assisted", record.get("mode").getAsString());
        assertEquals(1234, record.get("gameTick").getAsLong());
    }

    @Test void smallLiveLogsFlushBeforeTheWorldCloses() throws Exception {
        Path file = temp.resolve("live.jsonl");
        TelemetryLog.start(file, "test-version", "test-dataset");
        try {
            var trial = new TrialSession().start("pulse-1", 100, 40);
            TelemetryLog.record(new BrainTelemetryPayload(7, 12, 3, 0, 0, 0, 1, false),
                    UUID.randomUUID(), "minecraft:overworld", 102, trial, 77);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
            while ((!Files.exists(file) || Files.readAllLines(file).size() < 2)
                    && System.nanoTime() < deadline) Thread.sleep(10);
            var lines = Files.readAllLines(file);
            assertEquals(2, lines.size());
            var sample = JsonParser.parseString(lines.get(1)).getAsJsonObject();
            assertEquals(77, sample.get("neuralTick").getAsLong());
            assertEquals(100, sample.get("trialStartedAt").getAsLong());
            assertEquals(2, sample.get("trialTick").getAsLong());
        } finally { TelemetryLog.stop(); }
    }

    @Test void asyncWriterRetainsTheExpandedActiveNeuronSampleAndDriveMetrics() throws Exception {
        Path file = temp.resolve("high-detail.jsonl");
        int[] ids = java.util.stream.IntStream.range(0, 25_600).toArray();
        var packet = new BrainTelemetryPayload(7, 12, 0L, 3, 0, 0, 0, 1, false,
                0, 0, 0, 0, 0, 0, 0, 0.2f, 0.3f, 0, 0, 0, false, false, false,
                lab.neurolab.brain.ControlMode.ASSISTED, 0.55f, 0.65f, 0.35f, 31, 8, ids);
        TelemetryLog.start(file);
        try {
            TelemetryLog.record(packet, UUID.randomUUID(), "minecraft:overworld", 1234, null);
        } finally { TelemetryLog.stop(); }

        var sample = JsonParser.parseString(Files.readAllLines(file).get(1)).getAsJsonObject();
        assertEquals(25_600, sample.getAsJsonArray("firingNeuronIds").size());
        assertEquals(0.65, sample.get("satiety").getAsDouble());
        assertEquals(0.35, sample.get("hydration").getAsDouble());
        assertEquals(31, sample.get("learnedSynapseCount").getAsInt());
        assertEquals(8, sample.get("learningRevision").getAsInt());
    }

    @Test void stoppedRecordsDoNotLeakIntoTheNextWorld() throws Exception {
        Path first = temp.resolve("first.jsonl"), second = temp.resolve("second.jsonl");
        TelemetryLog.start(first);
        TelemetryLog.stop();
        TelemetryLog.record(new BrainTelemetryPayload(99, 12, 3, 0, 0, 0, 1, false),
                UUID.randomUUID(), "minecraft:overworld", 5, null);
        TelemetryLog.start(second);
        TelemetryLog.stop();
        assertEquals(2, Files.readAllLines(second).size());
        var a = JsonParser.parseString(Files.readAllLines(first).getFirst()).getAsJsonObject();
        var b = JsonParser.parseString(Files.readAllLines(second).getFirst()).getAsJsonObject();
        assertNotEquals(a.get("runId"), b.get("runId"));
        assertFalse(TelemetryLog.status().running());
        assertEquals(0, TelemetryLog.status().queued());
    }

    @Test void diskFailureIsVisibleAndANewSessionCanRecover() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("not-a-file"));
        TelemetryLog.start(directory);
        TelemetryLog.stop();
        assertTrue(TelemetryLog.status().failed());
        Path recovered = temp.resolve("recovered.jsonl");
        TelemetryLog.start(recovered);
        TelemetryLog.stop();
        assertFalse(TelemetryLog.status().failed());
        assertEquals(2, Files.readAllLines(recovered).size());
    }

    @Test void positionIntervalsDoNotCrossMissingSamplesLongGapsOrTrialBoundaries() throws Exception {
        Path file = temp.resolve("positions.jsonl");
        UUID uuid = UUID.randomUUID();
        var p = new BrainTelemetryPayload(7, 12, 3, 0, 0, 0, 1, false);
        TelemetryLog.start(file);
        try {
            TelemetryLog.record(p, uuid, "minecraft:overworld", 10, null, 1, 1, 64, 2);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 12, null, 2, 2, 65, 4);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 14, null, 3);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 16, null, 4, 3, 65, 5);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 300, null, 5, 4, 65, 6);
            var trial = new TrialSession().start("pulse", 302, 40);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 302, trial, 6, 5, 66, 7);
            TelemetryLog.record(p, uuid, "minecraft:overworld", 304, trial, 7, 6, 66, 8);
        } finally { TelemetryLog.stop(); }
        var samples = Files.readAllLines(file).stream().map(line -> JsonParser.parseString(line).getAsJsonObject())
                .filter(line -> line.get("event").getAsString().equals("sample")).toList();
        assertEquals(java.util.List.of(false, true, false, false, false, false, true),
                samples.stream().map(s -> s.get("positionDeltaValid").getAsBoolean()).toList());
        assertEquals(1, samples.get(1).get("deltaX").getAsDouble());
        assertEquals(1, samples.get(1).get("deltaY").getAsDouble());
        assertEquals(2, samples.get(1).get("deltaZ").getAsDouble());
        assertEquals(2, samples.get(1).get("positionDeltaTicks").getAsInt());
        assertTrue(samples.get(2).get("x").isJsonNull());
    }
}
