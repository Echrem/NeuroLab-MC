package lab.neurolab.minecraft;

import lab.neurolab.brain.StimulusSchedule;

/** Discrete stimulation contacts available on the in-world petri station. */
public enum ExperimentElectrode implements net.minecraft.util.StringRepresentable {
    EYE(StimulusSchedule.Sense.EYE, "center eye"),
    LEFT_EYE(StimulusSchedule.Sense.LEFT_EYE, "left eye"),
    RIGHT_EYE(StimulusSchedule.Sense.RIGHT_EYE, "right eye"),
    TOUCH(StimulusSchedule.Sense.TOUCH, "touch"),
    ODOR(StimulusSchedule.Sense.ODOR, "odor"),
    TASTE(StimulusSchedule.Sense.TASTE, "taste");

    private final StimulusSchedule.Sense sense;
    private final String label;

    ExperimentElectrode(StimulusSchedule.Sense sense, String label) {
        this.sense = sense;
        this.label = label;
    }

    public StimulusSchedule.Sense sense() { return sense; }
    public String label() { return label; }
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public ExperimentElectrode next() { return values()[(ordinal() + 1) % values().length]; }
    public static ExperimentElectrode byName(String name) {
        for (ExperimentElectrode electrode : values())
            if (electrode.getSerializedName().equals(name)) return electrode;
        return EYE;
    }
}
