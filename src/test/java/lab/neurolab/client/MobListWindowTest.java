package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobListWindowTest {
    @Test
    void everyTrackedSubjectRemainsReachableThroughBoundedScrollWindows() {
        MobListWindow window = MobListWindow.calculate(100, 116);
        assertTrue(window.visibleCount() < 100);
        assertEquals(100 - window.visibleCount(), window.maxFirstIndex());
        assertEquals(99, window.indexAt(20 + (window.visibleCount() - 1) * window.rowHeight(), 20,
                window.maxFirstIndex()));
    }

    @Test
    void rejectsClicksOutsideVisibleRowsAndClampsScrolling() {
        MobListWindow window = MobListWindow.calculate(8, 116);
        assertEquals(-1, window.indexAt(20 + window.visibleCount() * window.rowHeight(), 20, 0));
        assertEquals(0, window.clampFirstIndex(-10));
        assertEquals(window.maxFirstIndex(), window.clampFirstIndex(100));
    }
}
