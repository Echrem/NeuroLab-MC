package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialSessionTest {
    @Test void deadlinesAreExclusiveAndExpiryReturnsTheOriginalBoundary() {
        TrialSession session = new TrialSession();
        var trial = session.start("baseline-1", 100, 40);
        assertNull(session.current(99));
        assertEquals(trial, session.current(139));
        assertNull(session.current(140));
        assertEquals(trial, session.expire(145));
        assertEquals(140, trial.endsAtTick());
        assertNull(session.end());
    }

    @Test void invalidStartsPreserveTheActiveTrial() {
        TrialSession session = new TrialSession();
        var trial = session.start("valid", 10, 40);
        assertThrows(IllegalArgumentException.class, () -> session.start("bad label", 20, 40));
        assertThrows(IllegalArgumentException.class, () -> session.start("valid", 20, 12001));
        assertThrows(ArithmeticException.class, () -> session.start("valid", Long.MAX_VALUE, 1));
        assertEquals(trial, session.current(20));
    }
}
