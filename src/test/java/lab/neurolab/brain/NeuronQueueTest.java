package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeuronQueueTest {
    @Test void repeatedInputsAndGrowthPreserveEachNeuronExactlyOnce() {
        NeuronQueue queue = new NeuronQueue(10_000);
        for (int i = 0; i < 10_000; i++) {
            queue.add(i);
            queue.add(i);
            queue.add(0);
        }
        assertEquals(10_000, queue.size());
        for (int i = 0; i < queue.size(); i++) assertEquals(i, queue.get(i));
    }

    @Test void schedulingNextStepCannotEraseMembershipInTheCurrentStep() {
        NeuronQueue current = new NeuronQueue(3);
        NeuronQueue next = new NeuronQueue(3);
        current.add(0); current.add(1);
        next.add(1); // A synapse from the first current neuron targets the second.
        next.add(current.get(1)); // Integrating the second neuron must not duplicate it.
        assertEquals(1, next.size());
        current.clear();
        assertEquals(1, next.size());
        current.add(1);
        assertEquals(1, current.size());
    }
}
