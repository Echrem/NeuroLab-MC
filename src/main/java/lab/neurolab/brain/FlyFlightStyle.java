package lab.neurolab.brain;

/** Deterministic, bounded flight texture layered around the decoded motor command. */
public final class FlyFlightStyle {
    public record Motion(double turn, double speedScale, double lift) {}

    private FlyFlightStyle() {}

    public static Motion sample(long identity, long gameTick) {
        double t = Math.max(0, gameTick);
        double slowSway = Math.sin(t * 0.071 + phase(identity, 0x41L)) * 0.14;
        double drift = Math.sin(t * 0.029 + phase(identity, 0xA7L)) * 0.075;
        double saccadeWave = Math.sin(t * 0.093 + phase(identity, 0x19DL));
        double saccade = Math.copySign(Math.pow(Math.abs(saccadeWave), 10), saccadeWave) * 0.52;
        double speed = 0.88
                + Math.sin(t * 0.057 + phase(identity, 0x2F3L)) * 0.12
                + Math.sin(t * 0.173 + phase(identity, 0x6D1L)) * 0.07;
        double lift = Math.sin(t * 0.211 + phase(identity, 0x913L)) * 0.075
                + Math.sin(t * 0.063 + phase(identity, 0xB4DL)) * 0.035;
        return new Motion(clamp(slowSway + drift + saccade, -0.72, 0.72),
                clamp(speed, 0.65, 1.08), clamp(lift, -0.11, 0.11));
    }

    private static double phase(long identity, long salt) {
        long value = identity ^ (salt * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53 * Math.PI * 2;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
