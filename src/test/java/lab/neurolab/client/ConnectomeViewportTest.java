package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectomeViewportTest {
    @Test
    void compactGuiGivesTheBrainARealCanvasAndReachableControls() {
        for (int width : new int[]{320, 480, 640}) {
            var viewport = ConnectomeViewport.calculate(width, 240);
            assertTrue(viewport.compact());
            assertTrue(viewport.right() - viewport.left() >= width - 32);
            assertTrue(viewport.bottom() - viewport.top() >= 140);
            assertTrue(viewport.inDensityButton(viewport.densityButtonX() + 2, viewport.buttonY() + 2));
            assertTrue(viewport.inFilterButton(viewport.filterButtonX() + 2, viewport.buttonY() + 2));
            assertFalse(viewport.inDensityButton(viewport.right() / 2, viewport.top() + 30));
        }
    }

    @Test
    void wideGuiRetainsSeparateNeuronDetails() {
        var viewport = ConnectomeViewport.calculate(854, 480);
        assertFalse(viewport.compact());
        assertTrue(viewport.right() - viewport.left() >= 500);
    }
}
