package lab.neurolab.brain;

/** Small deterministic policies for sensory guidance, separated for repeatable tests. */
public final class FlyNavigation {
    public enum LightMode { BALANCED, SEEK_LIGHT, SEEK_SHADE, OFF }

    private FlyNavigation() {}

    /** Positive is a right turn; bilateral rays provide the light gradient. */
    public static double phototaxisTurn(double left, double right, double ambient,
                                        LightMode mode, double strength) {
        left = unit(left);
        right = unit(right);
        ambient = unit(ambient);
        strength = unit(strength);
        double preference = switch (mode) {
            case OFF -> 0;
            case SEEK_LIGHT -> 1;
            case SEEK_SHADE -> -1;
            case BALANCED -> ambient < 0.35 ? 1 : ambient > 0.82 ? -0.55 : 0.15;
        };
        return clamp((right - left) * preference * strength, -1, 1);
    }

    public static boolean mayPerch(boolean enabled, boolean threat, boolean raining,
                                   boolean onCooldown, double distance) {
        return enabled && !threat && !raining && !onCooldown && Double.isFinite(distance)
                && distance >= 0 && distance <= 1.25;
    }

    /** Hostile/player bodies trigger a wider looming cue; passive bodies loom only when closing nearby. */
    public static double loomingStrength(double distance, boolean closing, boolean dangerous) {
        double range = dangerous ? 8.0 : 3.5;
        if (!Double.isFinite(distance) || distance < 0 || distance >= range) return 0;
        if (!dangerous && (!closing || distance > 2.0)) return 0;
        return (1 - distance / range) * (closing ? 1.0 : dangerous ? 0.55 : 0.35);
    }

    /** Reduce approach speed continuously with distance, preserving the decoded throttle ceiling. */
    public static double approachThrottle(double decodedThrottle, double distance, double cueStrength) {
        double throttle = unit(decodedThrottle);
        if (!Double.isFinite(distance) || distance <= 0) return 0;
        distance = Math.min(16, distance);
        cueStrength = unit(cueStrength);
        double cueLimit = 0.20 + cueStrength * 0.60;
        double contactLimit = distance * 0.18;
        return Math.min(throttle, Math.min(cueLimit, contactLimit));
    }

    /** A bounded lift command that stabilizes a fly near its preferred height above local terrain. */
    public static double altitudeCommand(double currentY, double groundY, double targetAgl,
                                         double ceilingAgl, double neuralLift) {
        if (!Double.isFinite(currentY) || !Double.isFinite(groundY)
                || !Double.isFinite(targetAgl) || !Double.isFinite(ceilingAgl)) return 0;
        double agl = currentY - groundY;
        if (agl <= 0.9 && targetAgl > agl + 0.1) return 0.45;
        if (agl >= ceilingAgl) return -0.45;
        double hold = clamp((targetAgl - agl) * 0.24, -0.32, 0.32);
        double neural = targetAgl >= 1.0 ? unit(neuralLift) * 0.12 : 0;
        return clamp(hold + neural, -0.45, 0.45);
    }

    /** Enforce the terrain-relative ceiling on the actual movement step, not just the steering command. */
    public static double capAscentAtCeiling(double currentY, double groundY, double ceilingAgl,
                                            double proposedVerticalSpeed) {
        if (!Double.isFinite(currentY) || !Double.isFinite(groundY)
                || !Double.isFinite(ceilingAgl) || !Double.isFinite(proposedVerticalSpeed)) return 0;
        double remaining = groundY + Math.max(0, ceilingAgl) - currentY;
        if (remaining <= 0) return Math.min(0, proposedVerticalSpeed);
        return proposedVerticalSpeed > remaining ? remaining : proposedVerticalSpeed;
    }

    /** Keep an un-guided fly exploring without turning a sensory target into a stop command. */
    public static double guidedThrottle(double decodedThrottle, double targetStrength) {
        double base = unit(decodedThrottle);
        return targetStrength > 0.05 ? Math.max(base, 0.26) : base;
    }

    /** Limit horizontal speed changes so the embodied fly accelerates and brakes instead of snapping. */
    public static double approachVelocity(double current, double desired, double maxChange) {
        if (!Double.isFinite(current) || !Double.isFinite(desired) || !Double.isFinite(maxChange)) return 0;
        double step = Math.max(0, maxChange);
        return current + Math.max(-step, Math.min(step, desired - current));
    }

    /** Keep shelter seeking active across the scan radius, with a stronger correction nearby. */
    public static double shelterGuidanceStrength(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > 32) return 0;
        return clamp(1 - distance / 40, 0.2, 1);
    }

    /** Preserve direct refuge seeking while allowing the local collision sensor to steer around blocks. */
    public static double shelterTurn(double targetTurn, double avoidanceTurn, double obstacle) {
        return clamp(targetTurn + clamp(avoidanceTurn, -1, 1) * unit(obstacle) * 0.85, -1, 1);
    }

    /** PD lift controller that guides the fly into a covered block without climbing over its roof. */
    public static double shelterLiftCommand(double currentY, double targetY, double verticalSpeed) {
        if (!Double.isFinite(currentY) || !Double.isFinite(targetY) || !Double.isFinite(verticalSpeed)) return 0;
        return clamp((targetY - currentY) * 0.45 - verticalSpeed * 1.4, -0.45, 0.45);
    }

    /** Brake to a hold once the subject occupies the selected sheltered cell. */
    public static double shelterThrottle(double decodedThrottle, double distance, double cueStrength) {
        if (!Double.isFinite(distance) || distance <= 0.12) return 0;
        return approachThrottle(guidedThrottle(decodedThrottle, cueStrength), distance, cueStrength);
    }

    /** Smooth descent with extra upward braking immediately before floor contact. */
    public static double landingLiftCommand(double currentY, double groundY, double verticalSpeed) {
        if (!Double.isFinite(currentY) || !Double.isFinite(groundY) || !Double.isFinite(verticalSpeed)) return 0;
        double error = groundY + 0.03 - currentY;
        return clamp(error * 0.32 - verticalSpeed * 1.4, -0.45, 0.08);
    }

    /** Smooth horizontal progress with a lateral arc that vanishes at touchdown. */
    public static double landingEase(double progress) {
        double t = unit(progress);
        return t * t * (3 - 2 * t);
    }

    public static double landingSideOffset(double progress) {
        return Math.sin(Math.PI * unit(progress)) * 0.42;
    }

    public static boolean mayClingToWall(boolean threat, boolean exposedToRain) {
        return !threat && !exposedToRain;
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? clamp(value, 0, 1) : 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
