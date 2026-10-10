package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChunkManagerTest extends PluginTestBase {

    private Chunk chunk(int x, int z) {
        return world.getChunkAt(x, z);
    }

    /** A chunk whose block search reports the given materials, since MockBukkit chunks hold no blocks. */
    private static Chunk chunkContaining(Material... materials) {
        Chunk chunk = mock(Chunk.class);
        boolean[] forced = {false};
        doAnswer(call -> forced[0] = call.getArgument(0)).when(chunk).setForceLoaded(anyBoolean());
        when(chunk.isForceLoaded()).thenAnswer(call -> forced[0]);
        when(chunk.contains(any(BlockData.class))).thenReturn(false);
        for (Material material : materials) {
            when(chunk.contains(argThat((BlockData data) -> data != null && data.getMaterial() == material))).thenReturn(true);
        }
        return chunk;
    }

    @Test
    void chunksAreAdjacentOnlyAlongAnEdge() {
        ChunkArea area = new ChunkArea(chunk(0, 0));

        assertTrue(area.isAdjacent(chunk(1, 0)));
        assertTrue(area.isAdjacent(chunk(0, -1)));
        assertFalse(area.isAdjacent(chunk(1, 1)), "diagonal");
        assertFalse(area.isAdjacent(chunk(2, 0)));
        assertFalse(area.isAdjacent(chunk(0, 0)), "the chunk itself");
    }

    @Test
    void areasTrackTheirChunksAndAbandonment() {
        ChunkArea area = new ChunkArea(chunk(0, 0));
        area.addChunk(chunk(0, 1));
        area.addChunks(Set.of(chunk(0, 2)));
        area.increaseDaysAbandoned();
        area.increaseDaysAbandoned();

        assertEquals(Set.of(chunk(0, 0), chunk(0, 1), chunk(0, 2)), area.getChunks());
        assertTrue(area.containsChunk(chunk(0, 2)));
        assertFalse(area.containsChunk(chunk(5, 5)));
        assertEquals(2, area.getDaysAbandoned());
        area.setDaysAbandoned(0);
        assertEquals(0, area.getDaysAbandoned());
    }

    @Test
    void theAreaHoldingAChunkCanBeFound() {
        ChunkArea first = new ChunkArea(chunk(0, 0));
        ChunkArea second = new ChunkArea(chunk(9, 9));
        ArrayList<ChunkArea> areas = new ArrayList<>(List.of(first, second));

        assertSame(second, ChunkArea.findAreaForChunk(areas, chunk(9, 9)));
        assertNull(ChunkArea.findAreaForChunk(areas, chunk(4, 4)));
        assertNull(ChunkArea.findAreaForChunk(null, chunk(0, 0)));
        assertNull(ChunkArea.findAreaForChunk(areas, null));
    }

    @Test
    void aLoneStableChunkStartsItsOwnArea() {
        ChunkManager.addChunk(chunk(0, 0));
        ChunkManager.addChunk(chunk(5, 5));

        assertEquals(2, PluginData.getChunkAreas().size());
        assertTrue(ChunkManager.isStableChunk(chunk(0, 0)));
        assertTrue(ChunkManager.isStableChunk(chunk(5, 5)));
        assertFalse(ChunkManager.isStableChunk(chunk(1, 1)));
    }

    @Test
    void aChunkBridgingTwoAreasMergesThemIntoOne() {
        ChunkManager.addChunk(chunk(0, 0));
        ChunkManager.addChunk(chunk(2, 0));
        assertEquals(2, PluginData.getChunkAreas().size());

        ChunkManager.addChunk(chunk(1, 0));

        assertEquals(1, PluginData.getChunkAreas().size());
        assertEquals(Set.of(chunk(0, 0), chunk(1, 0), chunk(2, 0)), PluginData.getChunkAreas().get(0).getChunks());
    }

    @Test
    void onlyAnimalsCountTowardsAnAreasPopulationAndChunksAreReleasedAfterwards() {
        Location in = new Location(world, 8, 64, 8);
        world.spawn(in, Cow.class);
        world.spawn(in, Horse.class);
        world.spawn(in, Zombie.class);
        Chunk loaded = chunk(0, 0);
        Chunk alreadyForced = chunk(1, 0);
        alreadyForced.setForceLoaded(true);
        ChunkArea area = new ChunkArea(new java.util.HashSet<>(Set.of(loaded, alreadyForced)));

        assertEquals(2, ChunkManager.animalsContained(area));
        assertFalse(loaded.isForceLoaded());
        assertTrue(alreadyForced.isForceLoaded());
    }

    @Test
    void areasNeedMoreSpaceOnceThePerChunkLimitIsExceeded() {
        Location in = new Location(world, 8, 64, 8);
        ChunkArea area = new ChunkArea(chunk(0, 0));
        for (int i = 0; i < Numbers.maxAnimalsPerChunk; i++) {
            world.spawn(in, Cow.class);
        }
        assertFalse(ChunkManager.spaceNeeded(area));

        Entity extra = world.spawn(in, Cow.class);
        assertTrue(ChunkManager.spaceNeeded(area));

        area.addChunk(chunk(1, 0));
        assertFalse(ChunkManager.spaceNeeded(area), "a second chunk doubles the allowance");
        extra.remove();
    }

    @Test
    void waterOrAWaterCauldronAnywhereInTheAreaCounts() {
        assertTrue(ChunkManager.containsWater(new ChunkArea(chunkContaining(Material.WATER))));
        assertTrue(ChunkManager.containsWater(new ChunkArea(chunkContaining(Material.WATER_CAULDRON))));
        assertFalse(ChunkManager.containsWater(new ChunkArea(chunkContaining(Material.STONE))));
        assertFalse(ChunkManager.containsWater(new ChunkArea(chunk(0, 0))));
    }

    @Test
    void findingWaterReleasesTheChunkItTemporarilyLoaded() {
        Chunk wet = chunkContaining(Material.WATER);

        assertTrue(ChunkManager.containsWater(new ChunkArea(wet)));

        assertFalse(wet.isForceLoaded());
        verify(wet).setForceLoaded(true);
        verify(wet).setForceLoaded(false);
    }

    @Test
    void chunksThatWereAlreadyForceLoadedStaySo() {
        Chunk wet = chunkContaining(Material.WATER);
        wet.setForceLoaded(true);

        ChunkManager.containsWater(new ChunkArea(wet));

        assertTrue(wet.isForceLoaded());
    }
}
