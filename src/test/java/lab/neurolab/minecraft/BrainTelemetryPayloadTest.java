package lab.neurolab.minecraft;

import io.netty.buffer.Unpooled;
import lab.neurolab.brain.ControlMode;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrainTelemetryPayloadTest {
    @Test void wireRoundTripPreservesEverySignalAndMode() {
        for (ControlMode mode : ControlMode.values()) {
            var packet = new BrainTelemetryPayload(123, 456, 9_876_543_210L, 789, 0.1f, -0.2f, 0.3f,
                    0.4f, true, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1, 0.2f,
                    0.45f, 0.55f, 0.3f, -0.4f, 0.5f, false, true, true, mode, 0.73f,
                    new int[]{17, 42, 99});
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                BrainTelemetryPayload.CODEC.encode(buffer, packet);
                BrainTelemetryPayload decoded = BrainTelemetryPayload.CODEC.decode(buffer);
                assertEquals(packet.entityId(), decoded.entityId());
                assertEquals(packet.synapticEvents(), decoded.synapticEvents());
                assertEquals(packet.mode(), decoded.mode());
                assertArrayEquals(packet.firingNeuronIds(), decoded.firingNeuronIds());
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test void cameraFeedUsesASeparatePayloadTypeWithTheSameObservation() {
        var sample = new BrainTelemetryPayload(123, 456, 9_876_543_210L, 789, 0.1f, -0.2f, 0.3f,
                0.4f, true, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1, 0.2f,
                0.45f, 0.55f, 0.3f, -0.4f, 0.5f, false, true, true, ControlMode.ASSISTED, 0.73f,
                0.65f, 0.35f, 91, 18, new int[]{17, 42});
        var packet = new CameraTelemetryPayload(sample);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CameraTelemetryPayload.CODEC.encode(buffer, packet);
            var decoded = CameraTelemetryPayload.CODEC.decode(buffer);
            assertEquals(packet.sample(), decoded.sample());
            assertEquals(CameraTelemetryPayload.TYPE, decoded.type());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void activeNeuronSamplesAreDefensivelyCopiedAndBounded() {
        int[] source = {3, 8};
        var payload = new BrainTelemetryPayload(1, 0, 0L, 0, 0, 0, 0, 1, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, false, false,
                ControlMode.ASSISTED, source);
        source[0] = 999;
        int[] read = payload.firingNeuronIds();
        assertArrayEquals(new int[]{3, 8}, read);
        read[0] = 999;
        assertArrayEquals(new int[]{3, 8}, payload.firingNeuronIds());
    }

    @Test void activeNeuronSampleCapacityIsExpandedWithoutUnboundingPackets() {
        int[] ids = new int[BrainTelemetryPayload.MAX_FIRING_NEURON_IDS + 25];
        for (int i = 0; i < ids.length; i++) ids[i] = i;
        var payload = new BrainTelemetryPayload(1, 0, 0L, 0, 0, 0, 0, 1, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, false, false, false, ControlMode.ASSISTED, ids);

        assertEquals(65_536, BrainTelemetryPayload.MAX_FIRING_NEURON_IDS);
        assertEquals(65_536, payload.firingNeuronIds().length);
        assertEquals(65_535, payload.firingNeuronIds()[65_535]);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            BrainTelemetryPayload.CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 500_000);
            var decoded = BrainTelemetryPayload.CODEC.decode(buffer);
            assertEquals(65_536, decoded.firingNeuronCount());
            assertEquals(65_535, decoded.firingNeuronIds()[65_535]);
        } finally { buffer.release(); }
    }

    @Test void rosterWireRoundTripPreservesAttachedMobMetadataAndLatestTelemetry() {
        var telemetry = new BrainTelemetryPayload(9, 22, 8, 0.5f, 0.1f, 0.2f,
                1, false);
        var path = java.util.List.of(new lab.neurolab.brain.StructuralPathway.Step(99L, "R1", 0),
                new lab.neurolab.brain.StructuralPathway.Step(100L, "DNp09", 5));
        var packet = new BrainRosterPayload(java.util.List.of(new BrainRosterPayload.Entry(
                9, "Lab subject", "minecraft:villager", 12.5, 64, -3.25, telemetry)), path);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            BrainRosterPayload.CODEC.encode(buffer, packet);
            var decoded = BrainRosterPayload.CODEC.decode(buffer);
            assertEquals(packet.entries(), decoded.entries());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void synapseInspectionRoundTripPreservesDirectedEdgeEvidence() {
        var packet = new SynapseInspectionPayload(9, 17, 8, 120, 0.25f, 0.5f, -0.8f, -0.2f, 0.3f,
                3, -0.4f, "minecraft:cactus", -0.22f, 3,
                java.util.List.of(new SynapseInspectionPayload.Edge(42, 17, 23, 16, (byte) -1, -0.0125f)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SynapseInspectionPayload.CODEC.encode(buffer, packet);
            assertEquals(packet, SynapseInspectionPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
