package lab.neurolab.client;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NeuroViewPerspectiveTest {
    @Test
    void mobViewUsesFirstPersonAndRestoresThePreviousPerspective() {
        NeuroViewPerspective perspective = new NeuroViewPerspective();
        assertEquals(CameraType.FIRST_PERSON,
                perspective.update(CameraType.THIRD_PERSON_BACK, true));
        assertEquals(CameraType.FIRST_PERSON,
                perspective.update(CameraType.THIRD_PERSON_FRONT, true));
        assertTrue(perspective.active());
        assertEquals(CameraType.THIRD_PERSON_BACK,
                perspective.update(CameraType.FIRST_PERSON, false));
        assertFalse(perspective.active());
    }

    @Test
    void playerCameraDoesNotChangeWhenNoMobViewWasActive() {
        NeuroViewPerspective perspective = new NeuroViewPerspective();
        assertEquals(CameraType.THIRD_PERSON_FRONT,
                perspective.update(CameraType.THIRD_PERSON_FRONT, false));
    }
}
