package lab.neurolab.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FlyNavigationTest {
    @Test
    void flightStyleAddsStableBoundedSaccadesAndSpeedBouts() {
        var first = FlyFlightStyle.sample(1234L, 200);
        var repeated = FlyFlightStyle.sample(1234L, 200);
        var otherFly = FlyFlightStyle.sample(5678L, 200);
        assertEquals(first, repeated);
        assertNotEquals(first, otherFly);
        for (int tick = 0; tick < 2000; tick++) {
            var motion = FlyFlightStyle.sample(1234L, tick);
            assertTrue(Math.abs(motion.turn()) <= 0.72);
            assertTrue(motion.speedScale() >= 0.65 && motion.speedScale() <= 1.08);
            assertTrue(Math.abs(motion.lift()) <= 0.11);
        }
    }

    @Test
    void lightGradientTurnsTowardTheBrighterSideAtLowLight() {
        double turn = FlyNavigation.phototaxisTurn(0.9, 0.2, 0.1,
                FlyNavigation.LightMode.BALANCED, 0.8);
        assertTrue(turn < -0.5);
    }

    @Test
    void balancedModeMovesTowardShadeWhenAlreadyInBrightLight() {
        double turn = FlyNavigation.phototaxisTurn(0.9, 0.2, 0.95,
                FlyNavigation.LightMode.BALANCED, 1);
        assertTrue(turn > 0);
    }

    @Test
    void lightModesCanSeekShadeOrDisablePhototaxis() {
        assertTrue(FlyNavigation.phototaxisTurn(0.9, 0.2, 0.3,
                FlyNavigation.LightMode.SEEK_SHADE, 1) > 0);
        assertEquals(0.0, FlyNavigation.phototaxisTurn(0.9, 0.2, 0.3,
                FlyNavigation.LightMode.OFF, 1), 1.0e-9);
    }

    @Test
    void unsafeOrRainExposedFliesNeverPerchOnAHost() {
        assertTrue(FlyNavigation.mayPerch(true, false, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, true, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, true, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, false, true, 1.0));
        assertFalse(FlyNavigation.mayPerch(false, false, false, false, 1.0));
        assertFalse(FlyNavigation.mayPerch(true, false, false, false, Double.NaN));
    }

    @Test
    void passiveAnimalsAreNotThreatsUnlessTheyCloseFastAtShortRange() {
        assertEquals(0, FlyNavigation.loomingStrength(1.0, false, false));
        assertEquals(0, FlyNavigation.loomingStrength(2.5, true, false));
        assertTrue(FlyNavigation.loomingStrength(1.5, true, false) > 0.5);
        assertTrue(FlyNavigation.loomingStrength(3.0, false, true) > 0);
        assertTrue(FlyNavigation.loomingStrength(5.0, true, true) > 0.28);
        assertEquals(0, FlyNavigation.loomingStrength(8.0, true, true));
        assertEquals(0, FlyNavigation.loomingStrength(Double.POSITIVE_INFINITY, true, true));
    }

    @Test
    void approachSpeedBrakesSmoothlyAsTheLandingSurfaceGetsNearer() {
        double distant = FlyNavigation.approachThrottle(0.7, 4.0, 0.9);
        double near = FlyNavigation.approachThrottle(0.7, 1.0, 0.9);
        double contact = FlyNavigation.approachThrottle(0.7, 0.2, 0.9);
        assertTrue(distant > near);
        assertTrue(near > contact);
        assertTrue(contact < 0.05);
        assertEquals(0, FlyNavigation.approachThrottle(0.7, Double.NaN, 0.9));
    }

    @Test
    void targetGuidanceCannotTurnAQuietFlyIntoAStationaryOne() {
        assertEquals(0.26, FlyNavigation.guidedThrottle(0, 0.8), 1.0e-9);
        assertEquals(0.30, FlyNavigation.guidedThrottle(0.30, 0), 1.0e-9);
    }

    @Test
    void shelterGuidanceStaysUsefulAcrossTheSearchRadius() {
        assertEquals(1.0, FlyNavigation.shelterGuidanceStrength(0), 1.0e-9);
        assertTrue(FlyNavigation.shelterGuidanceStrength(16) > 0.5);
        assertTrue(FlyNavigation.shelterGuidanceStrength(31) > 0);
        assertEquals(0, FlyNavigation.shelterGuidanceStrength(33), 1.0e-9);
        assertEquals(0, FlyNavigation.shelterGuidanceStrength(Double.NaN), 1.0e-9);
    }

    @Test
    void shelterSteeringStillAvoidsLocalObstacles() {
        assertTrue(FlyNavigation.shelterTurn(0.4, 0, 1) > 0);
        assertTrue(FlyNavigation.shelterTurn(0.4, -0.8, 1) < 0);
        assertEquals(0.4, FlyNavigation.shelterTurn(0.4, -0.8, 0), 1.0e-9);
    }

    @Test
    void shelterVerticalControllerTracksHeightAndDampsMomentum() {
        assertTrue(FlyNavigation.shelterLiftCommand(5, 1, 0) < 0);
        assertTrue(FlyNavigation.shelterLiftCommand(1, 5, 0) > 0);
        assertTrue(FlyNavigation.shelterLiftCommand(2, 2, 0.3) < 0);
        assertEquals(-0.45, FlyNavigation.shelterLiftCommand(20, 1, 0), 1.0e-9);
        assertEquals(0, FlyNavigation.shelterLiftCommand(Double.NaN, 1, 0), 1.0e-9);
    }

    @Test
    void shelterThrottleBrakesInsideTheCoveredCell() {
        assertEquals(0, FlyNavigation.shelterThrottle(0.8, 0.1, 1), 1.0e-9);
        assertTrue(FlyNavigation.shelterThrottle(0.8, 0.5, 1) > 0);
        assertTrue(FlyNavigation.shelterThrottle(0.8, 1.2, 0.9) > 0);
        assertTrue(FlyNavigation.shelterThrottle(0.8, 1.2, 0.9) < 0.25);
    }

    @Test
    void flightVelocityAcceleratesAndBrakesWithoutOvershooting() {
        assertEquals(0.08, FlyNavigation.approachVelocity(0, 0.8, 0.08), 1.0e-9);
        assertEquals(0.72, FlyNavigation.approachVelocity(0.8, 0, 0.08), 1.0e-9);
        assertEquals(0.8, FlyNavigation.approachVelocity(0.8, 1, 0), 1.0e-9);
        assertEquals(0, FlyNavigation.approachVelocity(Double.NaN, 1, 0.1), 1.0e-9);
    }

    @Test
    void wallPerchingIsCancelledWhileRainExposed() {
        assertTrue(FlyNavigation.mayClingToWall(false, false));
        assertFalse(FlyNavigation.mayClingToWall(true, false));
        assertFalse(FlyNavigation.mayClingToWall(false, true));
    }

    @Test
    void landingControllerDescendsAndBrakesNearTheFloor() {
        assertTrue(FlyNavigation.landingLiftCommand(5, 1, 0) < 0);
        assertTrue(FlyNavigation.landingLiftCommand(1, 1, -0.3) > 0);
        assertEquals(-0.45, FlyNavigation.landingLiftCommand(20, 1, 0), 1.0e-9);
    }

    @Test
    void landingArcStartsAndFinishesOnItsTargetWithoutAJump() {
        assertEquals(0, FlyNavigation.landingEase(0), 1.0e-9);
        assertEquals(1, FlyNavigation.landingEase(1), 1.0e-9);
        assertEquals(0.5, FlyNavigation.landingEase(0.5), 1.0e-9);
        assertEquals(0, FlyNavigation.landingSideOffset(0), 1.0e-9);
        assertTrue(FlyNavigation.landingSideOffset(0.5) > 0.3);
        assertEquals(0, FlyNavigation.landingSideOffset(1), 1.0e-9);
    }

    @Test
    void lowRoofDoesNotForceTheFlyToClimbFromItsFloor() {
        assertEquals(0, FlyNavigation.altitudeCommand(64, 64, 0, 5, 1), 1.0e-9);
    }

    @Test
    void assistedAltitudeControlPushesUpBelowCruiseAndDownAtTheCeiling() {
        assertTrue(FlyNavigation.altitudeCommand(63, 64, 2.0, 5.0, 0) > 0);
        assertTrue(FlyNavigation.altitudeCommand(69, 64, 2.0, 5.0, 1) < 0);
        assertEquals(0.12, FlyNavigation.altitudeCommand(66, 64, 2.0, 5.0, 1), 1.0e-9);
    }

    @Test
    void altitudeCeilingCapsTheAppliedStepEvenWhenInertiaWouldOvershoot() {
        assertEquals(0.1, FlyNavigation.capAscentAtCeiling(68.9, 64, 5, 0.8), 1.0e-9);
        assertEquals(0, FlyNavigation.capAscentAtCeiling(69, 64, 5, 0.8), 1.0e-9);
        assertEquals(-0.2, FlyNavigation.capAscentAtCeiling(69.2, 64, 5, -0.2), 1.0e-9);
        assertEquals(0, FlyNavigation.capAscentAtCeiling(64, 64, 5, Double.NaN), 1.0e-9);
    }
}
