package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HusbandryEntitiesCacheTest {

    @AfterEach
    void tearDown() {
        HusbandryEntities.clearLoaded();
    }

    @Test
    void putLoadedIsCanonicalLookup() {
        UUID uuid = UUID.randomUUID();
        HusbandryAnimal animal = new HusbandryAnimal(uuid, "COW", "Bess");
        HusbandryEntities.putLoaded(animal);

        assertSame(animal, HusbandryEntities.getLoaded(uuid).orElseThrow());
        assertSame(animal, HusbandryEntities.lookup(uuid).orElseThrow());
        assertEquals(1, HusbandryEntities.snapshotLoaded().size());
    }

    @Test
    void evictRemovesCanonicalRecord() {
        UUID uuid = UUID.randomUUID();
        HusbandryEntities.putLoaded(new HusbandryAnimal(uuid, "SHEEP", "Wool"));
        HusbandryEntities.evict(uuid);

        assertTrue(HusbandryEntities.getLoaded(uuid).isEmpty());
        assertTrue(HusbandryEntities.snapshotLoaded().isEmpty());
    }

    @Test
    void snapshotDoesNotExposeLiveMutationByEviction() {
        HusbandryAnimal first = new HusbandryAnimal(UUID.randomUUID(), "PIG", "Ham");
        HusbandryEntities.putLoaded(first);
        List<HusbandryAnimal> snapshot = HusbandryEntities.snapshotLoaded();
        HusbandryEntities.clearLoaded();

        assertEquals(1, snapshot.size());
        assertTrue(HusbandryEntities.snapshotLoaded().isEmpty());
    }
}
