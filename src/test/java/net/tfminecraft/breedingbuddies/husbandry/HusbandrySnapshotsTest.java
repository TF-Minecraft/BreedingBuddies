package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.entity.Horse;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.papermc.paper.entity.EntitySerializationFlag;
import net.tfminecraft.breedingbuddies.BreedingBuddies;

class HusbandrySnapshotsTest {

    private static final UUID HORSE = UUID.fromString("fe3e69ee-6182-4036-bfcb-450779909327");
    private static final UUID OWNER = UUID.fromString("0615a817-8cb4-4aef-95f7-f6c9bf7611b8");

    private BreedingBuddies previousPlugin;
    private BreedingBuddies plugin;

    @BeforeEach
    void setUp() {
        previousPlugin = BreedingBuddies.plugin;
        plugin = mock(BreedingBuddies.class);
        when(plugin.isEnabled()).thenReturn(true);
        BreedingBuddies.plugin = plugin;
        HusbandryConfig.setRestoreLostAnimals(true);
    }

    @AfterEach
    void tearDown() {
        BreedingBuddies.plugin = previousPlugin;
        HusbandryConfig.setRestoreLostAnimals(true);
        HusbandryLocator.markFound(HORSE);
    }

    @Test
    void onlyUnloadsAndLoggedOutRidersKeepTheSnapshot() {
        for (EntityRemoveEvent.Cause cause : EntityRemoveEvent.Cause.values()) {
            boolean keeps = cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT;
            assertEquals(keeps, HusbandrySnapshots.keepsSnapshot(cause), cause.name());
        }
    }

    @Test
    void onlyOwnedAnimalsAreCaptured() {
        HusbandryAnimal animal = new HusbandryAnimal(HORSE, "HORSE", "Drake Maye");
        animal.setState(HusbandryAnimalState.OWNED);
        assertTrue(HusbandrySnapshots.shouldCapture(animal));
        animal.setState(HusbandryAnimalState.UNTAMED);
        assertFalse(HusbandrySnapshots.shouldCapture(animal));
        assertFalse(HusbandrySnapshots.shouldCapture(null));
    }

    @Test
    void captureForcesOnlyWhenAskedAndSwallowsRefusals() {
        Horse horse = mock(Horse.class);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.serializeEntity(horse)).thenReturn(new byte[] {1});
        when(unsafe.serializeEntity(horse, EntitySerializationFlag.FORCE)).thenReturn(new byte[] {2});
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);

            assertArrayEquals(new byte[] {1}, HusbandrySnapshots.capture(horse, false));
            assertArrayEquals(new byte[] {2}, HusbandrySnapshots.capture(horse, true));
            assertNull(HusbandrySnapshots.capture(null, false));

            when(unsafe.serializeEntity(horse)).thenThrow(new IllegalArgumentException("not persistent"));
            assertNull(HusbandrySnapshots.capture(horse, false));
        }
    }

    @Test
    void restoreSpawnsTheSnapshotWhereItWasTaken() {
        HusbandryAnimal animal = lostHorse();
        World world = mock(World.class);
        when(world.getName()).thenReturn("TFMC_Map");
        when(world.getChunkAtAsync(anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        Horse restored = mock(Horse.class);
        when(restored.getUniqueId()).thenReturn(HORSE);
        when(restored.getLocation()).thenReturn(new Location(null, 4353.0, 355.0, 3601.2));
        when(restored.spawnAt(any(Location.class), any(CreatureSpawnEvent.SpawnReason.class))).thenReturn(true);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.deserializeEntity(new byte[] {7}, world, true)).thenReturn(restored);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.getSnapshot(HORSE)).thenReturn(Optional.of(new HusbandrySnapshot(new byte[] {7}, 0L)));

        try (MockedStatic<Bukkit> bukkit = serverWith(world, unsafe);
             MockedStatic<HusbandryLifecycleListener> lifecycle = mockStatic(HusbandryLifecycleListener.class)) {
            assertTrue(HusbandrySnapshots.restore(repository, animal, owners()));

            verify(world).getChunkAtAsync(4353 >> 4, 3601 >> 4);
            verify(restored).spawnAt(new Location(world, 4353.0, 355.0, 3601.2),
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
            lifecycle.verify(() -> HusbandryLifecycleListener.handleLoad(restored));
        }
    }

    @Test
    void restoreFallsBackWhenThereIsNothingToSpawn() {
        HusbandryAnimal animal = lostHorse();
        World world = mock(World.class);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.getSnapshot(HORSE)).thenReturn(Optional.empty());
        try (MockedStatic<Bukkit> bukkit = serverWith(world, mock(UnsafeValues.class))) {
            assertFalse(HusbandrySnapshots.restore(repository, animal, owners()));

            when(repository.getSnapshot(HORSE)).thenReturn(Optional.of(new HusbandrySnapshot(new byte[] {7}, 0L)));
            HusbandryConfig.setRestoreLostAnimals(false);
            assertFalse(HusbandrySnapshots.restore(repository, animal, owners()));

            HusbandryConfig.setRestoreLostAnimals(true);
            bukkit.when(() -> Bukkit.getWorld("TFMC_Map")).thenReturn(null);
            assertFalse(HusbandrySnapshots.restore(repository, animal, owners()));
        }
        verify(world, never()).getChunkAtAsync(anyInt(), anyInt());
    }

    @Test
    void restoreRefusesASnapshotOfAnotherEntity() {
        HusbandryAnimal animal = lostHorse();
        World world = mock(World.class);
        Horse other = mock(Horse.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.deserializeEntity(new byte[] {7}, world, true)).thenReturn(other);
        HusbandryRepository repository = mock(HusbandryRepository.class);
        when(repository.getSnapshot(HORSE)).thenReturn(Optional.of(new HusbandrySnapshot(new byte[] {7}, 0L)));
        try (MockedStatic<Bukkit> bukkit = serverWith(world, unsafe)) {
            assertFalse(HusbandrySnapshots.restore(repository, animal, owners()));

            when(unsafe.deserializeEntity(new byte[] {7}, world, true)).thenThrow(new IllegalStateException("bad"));
            assertFalse(HusbandrySnapshots.restore(repository, animal, owners()));
        }
        verify(world, never()).getChunkAtAsync(anyInt(), anyInt());
    }

    @Test
    void anAnimalThatCameBackIsNotDuplicated() {
        HusbandryAnimal animal = lostHorse();
        Horse copy = mock(Horse.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HusbandryLifecycleListener> lifecycle = mockStatic(HusbandryLifecycleListener.class)) {
            bukkit.when(() -> Bukkit.getEntity(HORSE)).thenReturn(mock(Horse.class));

            HusbandrySnapshots.finish(animal, owners(), copy, new Location(null, 0, 0, 0), 0L);

            verify(copy, never()).spawnAt(any(Location.class), any(CreatureSpawnEvent.SpawnReason.class));
            lifecycle.verifyNoInteractions();
        }
    }

    @Test
    void aBlockedSpawnLeavesTheAnimalMissing() {
        HusbandryAnimal animal = lostHorse();
        Horse copy = mock(Horse.class);
        when(copy.spawnAt(any(Location.class), any(CreatureSpawnEvent.SpawnReason.class))).thenReturn(false);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<HusbandryLifecycleListener> lifecycle = mockStatic(HusbandryLifecycleListener.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));

            HusbandrySnapshots.finish(animal, owners(), copy, new Location(null, 0, 0, 0), 0L);

            assertTrue(HusbandryLocator.isMissing(HORSE));
            lifecycle.verifyNoInteractions();
        }
    }

    @Test
    void deliberateRemovalOfATrackedAnimalDropsItsSnapshot() {
        HusbandryRepository repository = mock(HusbandryRepository.class);
        UUID stranger = UUID.randomUUID();
        try (MockedStatic<HusbandryEntities> entities = mockStatic(HusbandryEntities.class)) {
            entities.when(HusbandryEntities::repository).thenReturn(repository);
            entities.when(() -> HusbandryEntities.getLoaded(HORSE)).thenReturn(Optional.of(lostHorse()));
            entities.when(() -> HusbandryEntities.getLoaded(stranger)).thenReturn(Optional.empty());

            HusbandryLifecycleListener.handleRemove(HORSE, EntityRemoveEvent.Cause.UNLOAD);
            HusbandryLifecycleListener.handleRemove(HORSE, EntityRemoveEvent.Cause.PLAYER_QUIT);
            verify(repository, never()).deleteSnapshot(HORSE);

            HusbandryLifecycleListener.handleRemove(HORSE, EntityRemoveEvent.Cause.PLUGIN);
            verify(repository).deleteSnapshot(HORSE);

            HusbandryLifecycleListener.handleRemove(stranger, EntityRemoveEvent.Cause.DISCARD);
            verify(repository, never()).deleteSnapshot(stranger);
        }
    }

    @Test
    void restoreLogNamesTheAnimalOwnersPlaceAndSnapshotTime() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("TFMC_Map");
        String line = HusbandrySnapshots.restoreLog(
                lostHorse(), owners(), new Location(world, 4353.0, 355.0, 3601.2), 1_790_000_000_000L);
        assertEquals("[BreedingBuddies] Restored lost animal Drake Maye (HORSE) " + HORSE + " owners=" + OWNER
                + " (owner) at TFMC_Map 4353, 355, 3601 from its snapshot of 2026-09-21T14:13:20Z", line);
    }

    private MockedStatic<Bukkit> serverWith(World world, UnsafeValues unsafe) {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class))).thenAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return null;
        });
        bukkit.when(() -> Bukkit.getWorld("TFMC_Map")).thenReturn(world);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));
        return bukkit;
    }

    private static HusbandryAnimal lostHorse() {
        HusbandryAnimal animal = new HusbandryAnimal(HORSE, "HORSE", "Drake Maye");
        animal.setState(HusbandryAnimalState.OWNED);
        animal.setLastLocation("TFMC_Map", 4352, 355, 3601);
        return animal;
    }

    private static List<HusbandryOwner> owners() {
        return List.of(new HusbandryOwner(HORSE, OWNER, OWNER, "owner"));
    }
}
