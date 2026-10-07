package lab.neurolab.minecraft;

/** Sensory channel mapping shared by the handheld probes and their tests. */
public enum StimulusProbeChannel {
    VISUAL("left_eye"),
    TACTILE("touch"),
    OLFACTORY("odor"),
    ACOUSTIC("sound"),
    GUSTATORY("taste"),
    LOOMING("looming");

    private final String sense;

    StimulusProbeChannel(String sense) { this.sense = sense; }

    public String sense() { return sense; }
}
