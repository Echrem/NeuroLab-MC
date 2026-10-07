package lab.neurolab.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentElectrodeTest {
    @Test
    void electrodeRingCoversVisualAndChemoSensoryChannelsThenWraps() {
        ExperimentElectrode electrode = ExperimentElectrode.EYE;
        assertEquals("center eye", electrode.label());
        assertEquals("LEFT_EYE", electrode.next().sense().name());
        assertEquals("RIGHT_EYE", electrode.next().next().sense().name());
        assertEquals("TOUCH", electrode.next().next().next().sense().name());
        assertEquals("ODOR", electrode.next().next().next().next().sense().name());
        assertEquals("TASTE", electrode.next().next().next().next().next().sense().name());
        assertEquals(ExperimentElectrode.EYE, electrode.next().next().next().next().next().next());
    }

    @Test
    void stimulusProbesTargetDistinctAnnotatedSensoryChannels() {
        assertEquals("left_eye", StimulusProbeChannel.VISUAL.sense());
        assertEquals("touch", StimulusProbeChannel.TACTILE.sense());
        assertEquals("odor", StimulusProbeChannel.OLFACTORY.sense());
    }
}
