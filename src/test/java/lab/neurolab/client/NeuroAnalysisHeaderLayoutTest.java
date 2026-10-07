package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuroAnalysisHeaderLayoutTest {
    @Test
    void monitorToolbarButtonsNeverOverlapAtCommonGuiWidths() {
        for (int screenWidth : new int[]{320, 400, 480, 560, 640, 854, 1920}) {
            for (boolean hasAttach : new boolean[]{false, true}) {
                NeuroAnalysisHeaderLayout layout = NeuroAnalysisHeaderLayout.calculate(screenWidth, 16, hasAttach);
                for (int i = 0; i < layout.buttonWidth().length; i++) {
                    assertTrue(layout.buttonWidth(i) > 0, "zero button width at " + screenWidth);
                    assertTrue(layout.buttonX(i) >= layout.panelLeft() + 18,
                            "button left edge at " + screenWidth);
                    assertTrue(layout.buttonX(i) + layout.buttonWidth(i)
                                    <= layout.panelLeft() + layout.panelWidth() - 18,
                            "button right edge at " + screenWidth);
                    for (int j = i + 1; j < layout.buttonWidth().length; j++) {
                        if (layout.buttonY(i) == layout.buttonY(j)) {
                            assertTrue(layout.buttonX(i) + layout.buttonWidth(i) <= layout.buttonX(j)
                                            || layout.buttonX(j) + layout.buttonWidth(j) <= layout.buttonX(i),
                                    "toolbar overlap at " + screenWidth + " between " + i + " and " + j);
                        }
                    }
                }
            }
        }
    }

    @Test
    void monitorToolbarAt640PixelsLeavesRoomForTheTitle() {
        NeuroAnalysisHeaderLayout layout = NeuroAnalysisHeaderLayout.calculate(640, 16, true);
        assertTrue(!layout.compact());
        assertTrue(layout.titleWidth() >= 80);
        assertTrue(layout.buttonX(0) > layout.panelLeft() + 18 + layout.titleWidth());
    }

    @Test
    void narrowScreensReflowActionsAndMoveDashboardContentBelowThem() {
        NeuroAnalysisHeaderLayout layout = NeuroAnalysisHeaderLayout.calculate(480, 16, true);
        assertTrue(layout.compact());
        assertTrue(layout.contentTopOffset() > 50);
        assertTrue(layout.buttonY(0) < layout.buttonY(layout.guideIndex()));
        assertTrue(layout.buttonY(layout.guideIndex()) < layout.contentTopOffset() + 16);
    }
}
