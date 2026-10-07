package lab.neurolab.client;

import net.minecraft.client.CameraType;

/** Temporarily uses the subject's eye camera while preserving the player's chosen perspective. */
final class NeuroViewPerspective {
    private CameraType previous;

    CameraType update(CameraType current, boolean viewingMob) {
        if (viewingMob) {
            if (previous == null) previous = current;
            return CameraType.FIRST_PERSON;
        }
        if (previous == null) return current;
        CameraType restore = previous;
        previous = null;
        return restore;
    }

    boolean active() { return previous != null; }
}
