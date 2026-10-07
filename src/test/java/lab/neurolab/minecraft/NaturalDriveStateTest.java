package lab.neurolab.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NaturalDriveStateTest {
    @Test
    void flowerAndTreeRewardsHavePerSubjectCooldowns() {
        NaturalDriveState subject = new NaturalDriveState();
        var flower = subject.update(100, false, false, 1.0, 10, Double.POSITIVE_INFINITY, 0, 1);
        assertTrue(flower.reward() > 0);
        var sameFlower = subject.update(150, false, false, 1.0, 10, Double.POSITIVE_INFINITY, 0, 1);
        assertEquals(0, sameFlower.reward());
        var afterCooldown = subject.update(400, false, false, 1.0, 10,
                Double.POSITIVE_INFINITY, 0, 1);
        assertEquals(0, afterCooldown.reward());
        assertTrue(subject.update(401, false, false, 1.0, 11,
                Double.POSITIVE_INFINITY, 0, 1).reward() > 0);

        var tree = subject.update(801, false, false, Double.POSITIVE_INFINITY, 0, 1.0, 22, 1);
        assertTrue(tree.reward() > 0);
        var sameTree = subject.update(1_202, false, false, Double.POSITIVE_INFINITY, 0, 1.0, 22, 1);
        assertEquals(0, sameTree.reward());
    }

    @Test
    void rainIsNegativeFeedbackAndExposureAccumulatesStress() {
        NaturalDriveState subject = new NaturalDriveState();
        var first = subject.update(1, true, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1);
        var prolonged = subject.update(101, true, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1);
        assertTrue(first.pain() > 0);
        assertTrue(prolonged.pain() > first.pain());
        assertEquals(1, prolonged.rainStress(), 1.0e-6);
        assertEquals(0, subject.update(301, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1).rainStress(), 1.0e-6);
    }

    @Test
    void waterRestoresHydrationWithoutAnImmediateRewardLoop() {
        NaturalDriveState subject = new NaturalDriveState(1, 0.2f, 0);
        var firstDrink = subject.update(1, false, true, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1);
        assertTrue(firstDrink.reward() > 0);
        assertTrue(firstDrink.hydration() > 0.2f);
        var repeatedContact = subject.update(50, false, true, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1);
        assertEquals(0, repeatedContact.reward());
    }

    @Test
    void explorationIsPerMobAndOnlyRewardsPreviouslyUnseenCells() {
        NaturalDriveState firstMob = new NaturalDriveState();
        NaturalDriveState secondMob = new NaturalDriveState();
        assertTrue(firstMob.update(1, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 17).reward() > 0);
        assertEquals(0, firstMob.update(2, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 17).reward());
        assertTrue(secondMob.update(2, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 17).reward() > 0);
    }

    @Test
    void subjectsDoNotReceiveAFreeExplorationRewardForTheirSpawnCell() {
        NaturalDriveState subject = new NaturalDriveState();
        subject.seedExplorationCell(17);
        assertEquals(0, subject.update(1, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 17).reward());
        assertTrue(subject.update(2, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 18).reward() > 0);
    }

    @Test
    void needsRemainBoundedAndDecayIndependently() {
        NaturalDriveState subject = new NaturalDriveState(0.8f, 0.7f, 0);
        var after = subject.update(12_001, false, false, Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, 0, 1);
        assertTrue(after.satiety() < 0.8f);
        assertTrue(after.hydration() < 0.7f);
        assertTrue(after.satiety() >= 0 && after.hydration() >= 0);
    }

    @Test
    void savedPlantAndExplorationHistoryPreventsRewardsAfterReattachment() {
        NaturalDriveState original = new NaturalDriveState();
        original.update(10, false, false, 1.0, 44, 1.0, 55, 91);
        NaturalDriveState restored = new NaturalDriveState(original.satiety(), original.hydration(),
                original.rainStress(), original.rewardedFlowers(), original.rewardedTrees(),
                original.visitedCells());

        var sameSites = restored.update(500, false, false, 1.0, 44, 1.0, 55, 91);
        assertEquals(0, sameSites.reward());
    }
}
