package lab.neurolab.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConnectomeProjectionTest {
    @Test void sharedScalePreservesRelativeAnatomicalAxes() {
        double longAxis = ConnectomeProjection.centeredAxis(100, 0, 100, 100);
        double shortAxis = ConnectomeProjection.centeredAxis(50, 0, 50, 100);

        assertEquals(0.5, longAxis, 1e-9);
        assertEquals(0.25, shortAxis, 1e-9);
        assertEquals(2.0, longAxis / shortAxis, 1e-9);
    }

    @Test void degenerateOrInvalidBoundsStayAtTheProjectionCenter() {
        assertEquals(0, ConnectomeProjection.centeredAxis(4, 4, 4, 0));
        assertEquals(0, ConnectomeProjection.centeredAxis(Float.NaN, 0, 1, 1));
    }
}
