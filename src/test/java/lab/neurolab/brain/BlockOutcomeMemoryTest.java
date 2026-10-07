package lab.neurolab.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BlockOutcomeMemoryTest {
    @Test void blockSpecificOutcomesAreGradualBoundedAndRestorable() {
        var cactus = ResourceLocation.parse("minecraft:cactus");
        var stone = ResourceLocation.parse("minecraft:stone");
        BlockOutcomeMemory memory = new BlockOutcomeMemory(new CompoundTag());
        memory.observe(cactus, 0, 0.7f);
        assertFalse(memory.avoids(cactus), "a single exposure begins learning without overreacting");
        memory.observe(cactus, 0, 0.7f);
        assertTrue(memory.preference(cactus) < 0);
        assertTrue(memory.avoids(cactus));
        assertEquals(0, memory.preference(stone));

        memory.observe(stone, 0.65f, 0);
        assertTrue(memory.preference(stone) > 0);
        BlockOutcomeMemory restored = new BlockOutcomeMemory(memory.save());
        assertEquals(memory.preference(cactus), restored.preference(cactus));
        assertEquals(memory.preference(stone), restored.preference(stone));
    }

    @Test void memoryRetainsOnlyTheMostRecentBoundedSetOfBlockTypes() {
        BlockOutcomeMemory memory = new BlockOutcomeMemory(new CompoundTag());
        for (int i = 0; i < 40; i++)
            memory.observe(ResourceLocation.fromNamespaceAndPath("test", "block_" + i), 0.5f, 0);
        assertEquals(32, memory.size());
        assertEquals(0, memory.preference(ResourceLocation.fromNamespaceAndPath("test", "block_0")));
        assertTrue(memory.preference(ResourceLocation.fromNamespaceAndPath("test", "block_39")) > 0);
    }
}
