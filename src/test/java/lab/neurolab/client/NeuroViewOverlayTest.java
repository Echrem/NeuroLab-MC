package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NeuroViewOverlayTest {
    @Test void suitHudAppearsDuringBrainMobCameraEvenWhileWaitingForTelemetry() {
        assertTrue(NeuroViewOverlay.shouldRender(true, false, true, false));
        assertFalse(NeuroViewOverlay.shouldRender(true, false, false, false));
        assertFalse(NeuroViewOverlay.shouldRender(true, false, true, true));
        assertFalse(NeuroViewOverlay.shouldRender(true, true, true, false));
        assertFalse(NeuroViewOverlay.shouldRender(false, false, true, false));
    }

    @Test void enteringMobCameraReenablesHudButPlayerCameraDoesNot() {
        assertTrue(NeuroViewOverlay.shouldEnableOnCameraChange(true, true, false));
        assertFalse(NeuroViewOverlay.shouldEnableOnCameraChange(false, true, false));
        assertFalse(NeuroViewOverlay.shouldEnableOnCameraChange(true, false, false));
        assertFalse(NeuroViewOverlay.shouldEnableOnCameraChange(true, true, true));
    }
}
