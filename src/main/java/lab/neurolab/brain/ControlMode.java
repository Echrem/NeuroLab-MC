package lab.neurolab.brain;

/** Assisted movement keeps ordinary pathfinding active; neural-only takes full body control. */
public enum ControlMode {
    ASSISTED, NEURAL, OBSERVE;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public boolean controlsBody() { return this != OBSERVE; }
    public boolean noAi(boolean originalNoAi) { return this == NEURAL || originalNoAi; }
}
