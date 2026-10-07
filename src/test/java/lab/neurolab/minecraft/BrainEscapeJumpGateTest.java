package lab.neurolab.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrainEscapeJumpGateTest {
    @Test
    void sustainedEscapeOnlyTriggersOneJumpUntilSignalResets() {
        BrainAttachmentService.EscapeJumpGate gate = new BrainAttachmentService.EscapeJumpGate();

        assertTrue(gate.shouldJump(true, true, 10));
        assertFalse(gate.shouldJump(true, true, 11));
        assertFalse(gate.shouldJump(true, true, 40));
        assertFalse(gate.shouldJump(false, true, 41));
        assertTrue(gate.shouldJump(true, true, 42));
    }

    @Test
    void airborneOrCooldownEscapeDoesNotJumpLaterWithoutFreshSignal() {
        BrainAttachmentService.EscapeJumpGate gate = new BrainAttachmentService.EscapeJumpGate();

        assertFalse(gate.shouldJump(true, false, 10));
        assertFalse(gate.shouldJump(true, true, 30));
        assertFalse(gate.shouldJump(false, true, 31));
        assertTrue(gate.shouldJump(true, true, 32));
    }

    @Test
    void groundedLandMobCanJumpAtAForwardObstacleWithoutRepeatedHopping() {
        BrainAttachmentService.EscapeJumpGate gate = new BrainAttachmentService.EscapeJumpGate();

        assertTrue(gate.shouldJump(false, true, 0.8, true, 100));
        assertFalse(gate.shouldJump(false, true, 0.8, true, 101));
        assertFalse(gate.shouldJump(false, true, 0.8, true, 130));
        assertFalse(gate.shouldJump(false, false, 0.8, true, 131));
        assertTrue(gate.shouldJump(false, true, 0.8, true, 132));
    }

    @Test
    void obstacleWithoutForwardDriveDoesNotCauseJump() {
        BrainAttachmentService.EscapeJumpGate gate = new BrainAttachmentService.EscapeJumpGate();

        assertFalse(gate.shouldJump(false, true, 0.05, true, 100));
    }
}
