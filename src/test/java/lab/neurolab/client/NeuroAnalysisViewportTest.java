package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuroAnalysisViewportTest {
    @Test
    void subjectDetailsLeaveUsefulPlotSpaceOnShortScreens() {
        for (int height : new int[]{240, 320, 360, 480, 720}) {
            var viewport = NeuroAnalysisViewport.calculate(height, 74, true, true);
            assertTrue(viewport.chartBottom() - viewport.chartTop() >= 90,
                    "details plot viewport too short at " + height);
            assertTrue(viewport.chartBottom() <= viewport.panelBottom());
        }
    }

    @Test
    void overviewStillHasPlotPreviewsAtSupportedCompactHeight() {
        var viewport = NeuroAnalysisViewport.calculate(320, 74, true, false);
        assertTrue(viewport.chartBottom() - viewport.chartTop() >= 60);
    }

    @Test
    void overviewUsesAvailableHeightForTheConnectomeWithoutHidingPlots() {
        var medium = NeuroAnalysisViewport.calculate(480, 50, false, false);
        var tall = NeuroAnalysisViewport.calculate(720, 50, false, false);
        assertTrue(medium.overviewHeight() >= 180);
        assertTrue(tall.overviewHeight() >= medium.overviewHeight());
        assertTrue(medium.chartBottom() - medium.chartTop() >= 120);
        assertTrue(tall.chartBottom() - tall.chartTop() >= 120);
    }
}
