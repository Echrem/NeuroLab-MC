package lab.neurolab.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BehaviorCatalogTest {
    @Test void keyBehaviorClaimsRemainInTheirActualLayer() {
        assertEquals(BehaviorCatalog.Layer.AUTOMATIC, layer("Rain shelter"));
        assertEquals(BehaviorCatalog.Layer.ASSISTED, layer("Collision steering"));
        assertEquals(BehaviorCatalog.Layer.LEARNED, layer("Flower attraction"));
        assertEquals(BehaviorCatalog.Layer.LEARNED, layer("Synaptic weights"));
        assertEquals(BehaviorCatalog.Layer.LEARNED, layer("Zombie pursuit"));
        assertEquals(BehaviorCatalog.Layer.LEARNED, layer("Attacker recognition"));
    }
    private static BehaviorCatalog.Layer layer(String name) {
        return BehaviorCatalog.entries().stream().filter(item -> item.name().equals(name))
                .findFirst().orElseThrow().layer();
    }
}
