package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.*;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;
import io.papermc.paper.entity.EntitySerializationFlag;

class HusbandryRuntimeCoverageTest {
    @TempDir Path directory;
    Farm f;
    private final HusbandryLifecycleListener lifecycle = new HusbandryLifecycleListener();
    @BeforeEach void setUp() throws Exception { f = new Farm(directory); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test void loadAndUnloadRestoreCareMaturityLocationAndSnapshots() {
        Horse horse = f.spawn(Horse.class); horse.teleport(new Location(f.world, -3.4, 68, 14.2));
        HusbandryAnimal animal = f.record(horse); f.own(animal, f.player); HusbandryEntities.evict(animal.uuid());
        animal.setMatureAt(System.currentTimeMillis() - 1); animal.setUnloadedAt(System.currentTimeMillis() - 1000); f.repository.upsertAnimal(animal);
        HusbandryLocator.markMissing(animal.uuid());
        lifecycle.onEntitiesLoad(new EntitiesLoadEvent(horse.getChunk(), List.of(horse, f.spawn(ItemDisplay.class))));
        HusbandryAnimal loaded = HusbandryEntities.getLoaded(animal.uuid()).orElseThrow();
        assertNull(loaded.unloadedAt()); assertNotNull(loaded.loadedVisitStart()); assertNull(loaded.matureAt());
        assertTrue(horse.isPersistent()); assertFalse(horse.getRemoveWhenFarAway()); assertTrue(HusbandryEntities.isManaged(horse));
        assertFalse(HusbandryLocator.isMissing(animal.uuid())); assertEquals(-4, loaded.x()); assertEquals(25, HusbandryMounts.maxHealth(horse));
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.serializeEntity(horse, EntitySerializationFlag.FORCE)).thenReturn(new byte[]{4, 2});
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            lifecycle.onEntitiesUnload(new EntitiesUnloadEvent(horse.getChunk(), List.of(horse, f.spawn(ItemDisplay.class))));
        }
        assertTrue(HusbandryEntities.getLoaded(animal.uuid()).isEmpty()); assertNotNull(f.repository.getAnimal(animal.uuid()).orElseThrow().unloadedAt());
        assertArrayEquals(new byte[]{4, 2}, f.repository.getSnapshot(animal.uuid()).orElseThrow().data());
        HusbandryLifecycleListener.handleLoad(horse);
        HusbandryLifecycleListener.handleRemove(animal.uuid(), EntityRemoveEvent.Cause.UNLOAD);
        HusbandryLifecycleListener.handleRemove(UUID.randomUUID(), EntityRemoveEvent.Cause.PLUGIN);
        assertTrue(f.repository.getSnapshot(animal.uuid()).isPresent());
        lifecycle.onEntityRemove(new EntityRemoveEvent(horse, EntityRemoveEvent.Cause.PLUGIN));
        assertTrue(f.repository.getSnapshot(animal.uuid()).isEmpty());
    }

    @Test void wipePolicyRemovesUnownedAnimalsButKeepsOwnersAndEnrolledMounts() throws Exception {
        f.config.set("remove-unowned", List.of("COW", "HORSE")); f.reload();
        Cow wild = f.spawn(Cow.class), tracked = f.spawn(Cow.class), owned = f.spawn(Cow.class);
        HusbandryAnimal discarded = f.record(tracked), protectedAnimal = f.record(owned); f.own(protectedAnimal, f.player);
        Horse mount = f.spawn(Horse.class), unknownMount = f.spawn(Horse.class); f.record(mount);
        HusbandryLifecycleListener.handleLoad(wild); HusbandryLifecycleListener.handleLoad(tracked); HusbandryLifecycleListener.handleLoad(owned);
        HusbandryLifecycleListener.handleLoad(mount); HusbandryLifecycleListener.handleLoad(unknownMount);
        assertFalse(wild.isValid()); assertFalse(tracked.isValid()); assertFalse(f.repository.exists(discarded.uuid())); assertTrue(HusbandryEntities.getLoaded(discarded.uuid()).isEmpty());
        assertTrue(owned.isValid()); assertTrue(mount.isValid()); assertFalse(unknownMount.isValid());
        Cow unrelated = f.spawn(Cow.class); when(f.plugin.getHusbandryRepository()).thenReturn(null); HusbandryLifecycleListener.handleLoad(unrelated); assertTrue(unrelated.isValid());
        when(f.plugin.getHusbandryRepository()).thenReturn(f.repository); Pig unmanaged = f.spawn(Pig.class); HusbandryLifecycleListener.handleLoad(unmanaged); assertFalse(HusbandryEntities.isManaged(unmanaged));
    }

    @Test void resumeAndRevisionRefreshPersistedStatsAndEvictDeletedRows() {
        Horse horse = f.spawn(Horse.class); HusbandryAnimal old = f.record(horse); old.setStatsRevision("old"); old.setCare(99); old.setGenetics(900); f.repository.upsertAnimal(old);
        HusbandryAnimal absentEntity = f.offline("COW", "Missing entity"); absentEntity.setStatsRevision("old"); f.repository.upsertAnimal(absentEntity); HusbandryEntities.putLoaded(absentEntity);
        HusbandryAnimal deleted = f.record(f.spawn(Cow.class)); f.repository.deleteAnimal(deleted.uuid()); f.spawn(ItemDisplay.class);
        HusbandryLifecycleListener.applyStatsRevision();
        HusbandryAnimal fresh = HusbandryEntities.getLoaded(old.uuid()).orElseThrow(); assertNotSame(old, fresh); assertEquals(0, fresh.care()); assertEquals(0, fresh.genetics()); assertEquals(HusbandryConfig.statsRevision(), fresh.statsRevision());
        assertTrue(HusbandryEntities.getLoaded(deleted.uuid()).isEmpty()); assertEquals(25, HusbandryMounts.maxHealth(horse));
        HusbandryEntities.clearLoaded(); HusbandryLifecycleListener.applyStatsRevision(); HusbandryLifecycleListener.resumeLoadedWorlds(); assertTrue(HusbandryEntities.getLoaded(old.uuid()).isPresent());
        when(f.plugin.getHusbandryRepository()).thenReturn(null); HusbandryLifecycleListener.applyStatsRevision();
    }

    @Test void shutdownFlushSavesLoadedAndMissingEntitiesAndCapturesOnlyOwnedAnimals() {
        Cow owned = f.spawn(Cow.class), wild = f.spawn(Cow.class), refused = f.spawn(Cow.class);
        HusbandryAnimal first = f.record(owned), second = f.record(wild), third = f.record(refused), absent = f.offline("COW", "Offline"); f.own(first, f.player); f.own(third, f.player); HusbandryEntities.putLoaded(absent);
        UnsafeValues unsafe = mock(UnsafeValues.class); when(unsafe.serializeEntity(owned)).thenReturn(new byte[]{8}); when(unsafe.serializeEntity(refused)).thenThrow(new IllegalArgumentException("not serializable"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe); HusbandryLifecycleListener.flushLoadedForDisable();
        }
        assertTrue(HusbandryEntities.loadedIds().isEmpty());
        for (HusbandryAnimal animal : List.of(first, second, third, absent)) assertNotNull(f.repository.getAnimal(animal.uuid()).orElseThrow().unloadedAt());
        assertArrayEquals(new byte[]{8}, f.repository.getSnapshot(first.uuid()).orElseThrow().data()); assertTrue(f.repository.getSnapshot(second.uuid()).isEmpty()); assertTrue(f.repository.getSnapshot(third.uuid()).isEmpty());
        HusbandryLifecycleListener.flushLoadedForDisable(); HusbandryEntities.putLoaded(first); when(f.plugin.getHusbandryRepository()).thenReturn(null); HusbandryLifecycleListener.flushLoadedForDisable(); assertTrue(HusbandryEntities.loadedIds().isEmpty());
    }

    @Test void unloadHandlesUntrackedAnimalsMissingRepositoriesAndRefusedSnapshots() {
        Cow cow = f.spawn(Cow.class); HusbandryLifecycleListener.handleUnload(cow);
        HusbandryAnimal animal = f.record(cow); HusbandryEntities.evict(animal.uuid()); HusbandryLifecycleListener.handleUnload(cow); assertNotNull(f.repository.getAnimal(animal.uuid()).orElseThrow().unloadedAt());
        f.own(animal, f.player); HusbandryEntities.putLoaded(animal);
        UnsafeValues unsafe = mock(UnsafeValues.class); when(unsafe.serializeEntity(cow, EntitySerializationFlag.FORCE)).thenThrow(new IllegalArgumentException("refused"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) { bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe); HusbandryLifecycleListener.handleUnload(cow); }
        assertTrue(f.repository.getSnapshot(animal.uuid()).isEmpty()); HusbandryEntities.putLoaded(animal); when(f.plugin.getHusbandryRepository()).thenReturn(null);
        HusbandryLifecycleListener.handleRemove(animal.uuid(), EntityRemoveEvent.Cause.PLUGIN); HusbandryLifecycleListener.handleUnload(cow); assertTrue(HusbandryEntities.getLoaded(animal.uuid()).isEmpty());
    }

    @Test void lifecycleLogsDatabaseFailuresWithoutLeavingLoadedShutdownState() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow);
        doThrow(new SqliteDatabaseException("snapshot unavailable")).when(f.repository).deleteSnapshot(animal.uuid());
        assertDoesNotThrow(() -> HusbandryLifecycleListener.handleRemove(animal.uuid(), EntityRemoveEvent.Cause.PLUGIN));
        doThrow(new SqliteDatabaseException("reset unavailable")).when(f.repository).resetStaleStats(anyString(), any());
        assertDoesNotThrow(HusbandryLifecycleListener::applyStatsRevision); assertSame(animal, HusbandryEntities.getLoaded(animal.uuid()).orElseThrow());
        doThrow(new SqliteDatabaseException("flush unavailable")).when(f.repository).upsertAnimals(anyList());
        HusbandryLifecycleListener.flushLoadedForDisable(); assertTrue(HusbandryEntities.loadedIds().isEmpty());
    }

    @Test void scanUpdatesLocationsSkipsRidersAndDeletesConfirmedGhosts() {
        HusbandryAnimal moved = f.ownedOffline("Moved"), same = f.ownedOffline("Same"), rider = f.ownedOffline("Rider"), ghost = f.ownedOffline("Ghost");
        same.setLastLocation("farm", 1, 2, 3); f.repository.upsertAnimal(same); HusbandryLocator.markMissing(rider.uuid());
        WorldMock nether = f.addWorld("nether"); nether.setEnvironment(World.Environment.NETHER);
        WorldMock end = f.addWorld("end"); end.setEnvironment(World.Environment.THE_END);
        var result = new HusbandryEntityScan.Result(Map.of(moved.uuid(), new HusbandryEntityScan.Found("nether", 9, 8, 7), same.uuid(), new HusbandryEntityScan.Found("farm", 1, 2, 3)), Set.of(rider.uuid()), true);
        try (Scan scan = new Scan(result)) {
            HusbandryLocator.scanUnloaded(); scan.background();
            assertEquals(List.of(new File(f.world.getWorldFolder(), "entities"), new File(nether.getWorldFolder(), "DIM-1/entities"), new File(end.getWorldFolder(), "DIM1/entities")), scan.worlds.stream().map(HusbandryEntityScan.WorldDir::entities).toList());
            scan.apply();
        }
        HusbandryAnimal saved = f.repository.getAnimal(moved.uuid()).orElseThrow(); assertEquals("nether", saved.world()); assertEquals(9, saved.x()); assertEquals(8, saved.y()); assertEquals(7, saved.z());
        assertTrue(f.repository.exists(rider.uuid())); assertFalse(HusbandryLocator.isMissing(rider.uuid())); assertFalse(f.repository.exists(ghost.uuid())); assertTrue(f.repository.listOwners(ghost.uuid()).isEmpty());
        assertEquals(1, f.repository.getAnimal(same.uuid()).orElseThrow().x());
    }

    @Test void scanDoesNotOverwriteNewerLoadsMovesDeletesOrUnscannedWorlds() {
        HusbandryAnimal loaded = f.ownedOffline("Loaded"), changed = f.ownedOffline("Changed"), deleted = f.ownedOffline("Deleted"), foreign = f.ownedOffline("Foreign");
        foreign.setLastLocation("unscanned", 1, 2, 3); f.repository.upsertAnimal(foreign);
        try (Scan scan = new Scan(new HusbandryEntityScan.Result(Map.of(), Set.of(), true))) {
            HusbandryLocator.scanUnloaded(); scan.background();
            HusbandryEntities.putLoaded(loaded); changed.setUnloadedAt(999L); f.repository.upsertAnimal(changed); f.repository.deleteAnimal(deleted.uuid()); scan.apply();
        }
        assertTrue(f.repository.exists(loaded.uuid())); assertEquals(999L, f.repository.getAnimal(changed.uuid()).orElseThrow().unloadedAt()); assertTrue(f.repository.exists(foreign.uuid()));
        HusbandryEntities.clearLoaded();
        try (Scan scan = new Scan(new HusbandryEntityScan.Result(Map.of(), Set.of(), false))) { HusbandryLocator.scanUnloaded(); scan.background(); scan.apply(); }
        assertTrue(f.repository.exists(loaded.uuid())); assertTrue(f.repository.exists(changed.uuid()));
    }

    @Test void scanWaitsForSnapshotRestorationAndKeepsFailedRecordsForRetry() {
        HusbandryAnimal restoring = f.ownedOffline("Restoring"), failedRead = f.ownedOffline("Failed read"), failedDrop = f.ownedOffline("Failed delete"), failedMove = f.ownedOffline("Failed write");
        doThrow(new SqliteDatabaseException("read failure")).when(f.repository).getSnapshot(failedRead.uuid());
        doThrow(new SqliteDatabaseException("delete failure")).when(f.repository).deleteAnimal(failedDrop.uuid());
        doThrow(new SqliteDatabaseException("write failure")).when(f.repository).upsertAnimal(argThat(a -> a != null && a.uuid().equals(failedMove.uuid())));
        try (MockedStatic<HusbandrySnapshots> snapshots = mockStatic(HusbandrySnapshots.class, CALLS_REAL_METHODS);
             Scan scan = new Scan(new HusbandryEntityScan.Result(Map.of(failedMove.uuid(), new HusbandryEntityScan.Found("farm", 3, 4, 5)), Set.of(), true))) {
            snapshots.when(() -> HusbandrySnapshots.restore(eq(f.repository), argThat(a -> a != null && a.uuid().equals(restoring.uuid())), anyList())).thenReturn(true);
            HusbandryLocator.scanUnloaded(); scan.background(); scan.apply();
        }
        assertTrue(f.repository.exists(restoring.uuid())); assertFalse(HusbandryLocator.isMissing(restoring.uuid())); assertTrue(HusbandryLocator.isMissing(failedRead.uuid())); assertTrue(HusbandryLocator.isMissing(failedDrop.uuid()));
        assertEquals(0, f.repository.getAnimal(failedMove.uuid()).orElseThrow().x());
    }

    @Test void scanningStopsAcrossRepositoryAndPluginShutdownBoundaries() {
        try (Scan scan = new Scan(new HusbandryEntityScan.Result(Map.of(), Set.of(), true))) {
            HusbandryLocator.scanUnloaded(); assertTrue(scan.async.isEmpty()); HusbandryAnimal owned = f.ownedOffline("Pending"); HusbandryEntities.putLoaded(owned); HusbandryLocator.scanUnloaded(); assertTrue(scan.async.isEmpty()); HusbandryEntities.clearLoaded();
            when(f.plugin.getHusbandryRepository()).thenReturn(null); HusbandryLocator.scanUnloaded(); assertTrue(scan.async.isEmpty()); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
            HusbandryLocator.scanUnloaded(); when(f.plugin.isEnabled()).thenReturn(false); scan.background(); assertTrue(scan.sync.isEmpty());
            when(f.plugin.isEnabled()).thenReturn(true); HusbandryLocator.scanUnloaded(); BreedingBuddies.plugin = null; scan.background(); assertTrue(scan.sync.isEmpty()); BreedingBuddies.plugin = f.plugin;
            HusbandryLocator.scanUnloaded(); scan.background(); when(f.plugin.getHusbandryRepository()).thenReturn(null); scan.apply(); assertTrue(f.repository.exists(owned.uuid()));
        }
        HusbandryLocator.markMissing(null); HusbandryLocator.markFound(null); assertFalse(HusbandryLocator.isMissing(null));
        assertTrue(HusbandryLocator.isConfirmedGhost(true, null, List.of("farm"))); assertTrue(HusbandryLocator.isConfirmedGhost(true, " ", List.of("farm"))); assertFalse(HusbandryLocator.isConfirmedGhost(true, "farm", null));
        HusbandryAnimal unnamed = new HusbandryAnimal(UUID.randomUUID(), "", ""); assertTrue(HusbandryLocator.describe(unnamed, null).startsWith("(unnamed) (unknown)")); assertTrue(HusbandryLocator.ghostLog(unnamed, List.of()).endsWith("last seen unknown"));
    }

    @Test void simulationTracksVisitThresholdsAndResetsOnlyFullyClearedAfflictions() {
        HusbandryAnimal animal = f.offline("COW", "Simulation"); animal.setLoadedVisitStart(null); assertFalse(HusbandrySimulator.visitLongEnough(null, 0)); assertFalse(HusbandrySimulator.visitLongEnough(animal, 0)); animal.setLoadedVisitStart(1000L);
        assertFalse(HusbandrySimulator.visitLongEnough(animal, 1000)); assertTrue(HusbandrySimulator.visitLongEnough(animal, 61_000)); assertFalse(HusbandrySimulator.isHappy(null));
        Random random = mock(Random.class); when(random.nextDouble()).thenReturn(0.5); animal.setAfflictionElapsed(2); animal.setHungrySince(1L); animal.setDirtySince(1L);
        HusbandrySimulator.clearHungry(animal, random); assertEquals(2, animal.afflictionElapsed()); HusbandrySimulator.clearDirty(animal, random); assertEquals(0, animal.afflictionElapsed()); assertEquals(6, animal.afflictionAt());
        animal.setHungrySince(1L); HusbandrySimulator.clearHungry(animal, random); assertTrue(HusbandrySimulator.isHappy(animal)); animal.setHungrySince(1L); animal.setDirtySince(1L); HusbandrySimulator.clearDirty(animal, random); assertFalse(HusbandrySimulator.isHappy(animal));
        HusbandrySimulator.clearHungry(null, random); HusbandrySimulator.clearDirty(null, random); HusbandrySimulator.resetAfflictionCycle(null, random); HusbandrySimulator.catchUp(null, 0, null);
        animal.setLastProcessedAt(100); HusbandrySimulator.tickLoaded(animal, 50); assertEquals(50, animal.lastProcessedAt()); HusbandrySimulator.tickLoaded(animal, 50, null); assertEquals(50, animal.lastProcessedAt());
    }

    @Test void unloadedCareIsCappedAndLongAbsencesApplyEachMissingAffliction() throws Exception {
        f.config.set("offline-care", "2h"); f.config.set("long-unload-force", "3h"); f.config.set("affliction-min-hours", 4); f.config.set("affliction-max-hours", 4); f.reload();
        for (boolean firstHungry : List.of(true, false)) {
            Random random = mock(Random.class); when(random.nextBoolean()).thenReturn(firstHungry);
            HusbandryAnimal animal = simulationAnimal(); animal.setUnloadedAt(0L);
            HusbandrySimulator.catchUp(animal, 4 * 3_600_000L, random); assertEquals(2, animal.care()); assertEquals(firstHungry, animal.hungrySince() != null); assertEquals(!firstHungry, animal.dirtySince() != null); assertEquals(4, animal.afflictionAt());
            animal.setUnloadedAt(animal.lastProcessedAt()); HusbandrySimulator.catchUp(animal, 8 * 3_600_000L, random); assertNotNull(animal.hungrySince()); assertNotNull(animal.dirtySince());
            animal.setUnloadedAt(animal.lastProcessedAt()); HusbandrySimulator.catchUp(animal, 12 * 3_600_000L, random); assertEquals(0, animal.afflictionElapsed());
        }
        HusbandryAnimal split = simulationAnimal(); split.setUnloadedAt(3_600_000L); HusbandrySimulator.catchUp(split, 2 * 3_600_000L, new Random(1)); assertEquals(2, split.care());
    }

    @Test void loadedAfflictionCyclesChooseEitherFirstStateAndEventuallyBoth() throws Exception {
        f.config.set("affliction-min-hours", 1); f.config.set("affliction-max-hours", 1); f.reload();
        for (boolean hungryFirst : List.of(true, false)) {
            HusbandryAnimal animal = simulationAnimal(); animal.setAfflictionAt(0); Random random = mock(Random.class); when(random.nextBoolean()).thenReturn(hungryFirst);
            HusbandrySimulator.tickLoaded(animal, 3_600_000, random); assertEquals(hungryFirst, animal.hungrySince() != null); assertEquals(!hungryFirst, animal.dirtySince() != null);
            HusbandrySimulator.tickLoaded(animal, 2 * 3_600_000, random); assertNotNull(animal.hungrySince()); assertNotNull(animal.dirtySince()); assertEquals(0, animal.afflictionElapsed());
        }
    }

    @Test void careDecayRespectsGraceAccumulatesRemaindersAndClampsAtBothEnds() throws Exception {
        f.config.set("decay-grace", "1h"); f.reload();
        HusbandryAnimal animal = simulationAnimal(); animal.setCare(3); animal.setDirtySince(0L);
        HusbandrySimulator.tickLoaded(animal, 3_600_000, new Random(1)); assertEquals(3, animal.care());
        HusbandrySimulator.tickLoaded(animal, 3_600_000 + 30_000, new Random(1)); assertEquals(30, animal.careDownRemainderSeconds());
        HusbandrySimulator.tickLoaded(animal, 2 * 3_600_000, new Random(1)); assertEquals(2, animal.care()); assertEquals(0, animal.careDownRemainderSeconds());
        animal.setHungrySince(0L); HusbandrySimulator.tickLoaded(animal, 10 * 3_600_000, new Random(1)); assertEquals(0, animal.care());
        HusbandryAnimal healthy = simulationAnimal(); healthy.setCare(100); HusbandrySimulator.tickLoaded(healthy, 3_600_000, new Random(1)); assertEquals(100, healthy.care());
        f.config.set("care-up-per-hour", 0); f.reload(); healthy.setCare(5); HusbandrySimulator.tickLoaded(healthy, 3_600_001, new Random(1)); HusbandrySimulator.tickLoaded(healthy, 2 * 3_600_000, new Random(1)); assertEquals(5, healthy.care());
    }

    @Test void mountEnrollmentAndScalingPreserveExistingRecordsAndRespectAttributeSupport() {
        Horse horse = f.spawn(Horse.class); assertNull(HusbandryMounts.enrollIfNeeded(null)); assertNull(HusbandryMounts.enrollIfNeeded(f.spawn(Cow.class)));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); assertNull(HusbandryMounts.enrollIfNeeded(horse)); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        HusbandryAnimal animal = HusbandryMounts.enrollIfNeeded(horse); assertNotNull(animal); assertSame(animal, HusbandryMounts.enrollIfNeeded(horse)); HusbandryEntities.evict(animal.uuid()); assertEquals(animal.uuid(), HusbandryMounts.enrollIfNeeded(horse).uuid());
        animal.setGenetics(1000); animal.setCare(100); HusbandryMounts.applyStats(horse, animal); assertEquals(25, HusbandryMounts.maxHealth(horse)); assertEquals(0.2, HusbandryMounts.speed(horse)); assertEquals(0.6, HusbandryMounts.jump(horse));
        assertEquals(12.5, HusbandryMounts.healthHearts(horse)); assertEquals(8.432, HusbandryMounts.speedBlocksPerSecond(horse), 1e-9); assertTrue(HusbandryMounts.jumpBlockHeight(horse) > 2);
        HusbandryMounts.setSpeed(horse, 0.2); HusbandryMounts.setMaxHealth(horse, 5); assertEquals(5, horse.getHealth());
        LivingEntity unsupported = mock(LivingEntity.class); HusbandryMounts.setMaxHealth(unsupported, 5); HusbandryMounts.setSpeed(unsupported, 0.5); assertEquals(-1, HusbandryMounts.maxHealth(unsupported)); assertEquals(-1, HusbandryMounts.speed(unsupported));
        Cow cow = f.spawn(Cow.class); HusbandryMounts.setJump(cow, 1); assertEquals(-1, HusbandryMounts.jump(cow)); assertEquals(-1, HusbandryMounts.jumpBlockHeight(cow));
        HusbandryMounts.applyStats(null, animal); HusbandryMounts.applyStats(horse, null); HusbandryMounts.applyStats(cow, animal); HusbandryMounts.applyStats(f.spawn(Donkey.class), animal);
        assertEquals(-1, HusbandryMounts.speedFor(null, EntityType.HORSE)); assertEquals(-1, HusbandryMounts.healthFor(animal, null)); assertEquals(-1, HusbandryMounts.jumpFor(animal, EntityType.COW));
        assertEquals(0, HusbandryMounts.mountFraction(-1, -1, 0, 0, -1, -1, -1)); assertEquals(0.9, HusbandryMounts.mountFraction(2000, 200, 1000, 100, 0.4, 0.3, 0.2), 1e-9);
        assertEquals(0.45, HusbandryMounts.speedFor(0.5, 1000, 100, 1000, 100, 0.4, 0.3, 0.2), 1e-9); assertFalse(HusbandryMounts.hasConfiguredStats(null)); assertFalse(HusbandryMounts.isMount(cow));
    }

    @Test void periodicTickPersistsCareAndSnapshotsThenStopsCleanly() {
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse); f.own(animal, f.player); animal.setLastProcessedAt(System.currentTimeMillis() - 3_600_000); animal.setLoadedVisitStart(1L);
        Cow recent = f.spawn(Cow.class); HusbandryAnimal notVisited = f.record(recent); notVisited.setLoadedVisitStart(System.currentTimeMillis()); long unchanged = notVisited.lastProcessedAt();
        Cow refused = f.spawn(Cow.class); HusbandryAnimal cannotCapture = f.record(refused); f.own(cannotCapture, f.player);
        HusbandryAnimal gone = f.offline("COW", "Gone"); HusbandryEntities.putLoaded(gone);
        ItemDisplay display = f.spawn(ItemDisplay.class); HusbandryAnimal nonLiving = new HusbandryAnimal(display.getUniqueId(), "ITEM_DISPLAY", "Display"); HusbandryEntities.putLoaded(nonLiving);
        UnsafeValues unsafe = mock(UnsafeValues.class); when(unsafe.serializeEntity(horse)).thenReturn(new byte[]{3}); when(unsafe.serializeEntity(refused)).thenThrow(new IllegalArgumentException("unavailable"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe); HusbandryTickTask.start(); HusbandryTickTask.start(); f.server.getScheduler().performTicks(1200);
            assertEquals(1, f.repository.getAnimal(animal.uuid()).orElseThrow().care()); assertEquals(unchanged, notVisited.lastProcessedAt()); assertArrayEquals(new byte[]{3}, f.repository.getSnapshot(animal.uuid()).orElseThrow().data()); assertTrue(f.repository.getSnapshot(cannotCapture.uuid()).isEmpty());
            assertTrue(HusbandryEntities.getLoaded(gone.uuid()).isEmpty()); assertTrue(HusbandryEntities.getLoaded(nonLiving.uuid()).isEmpty());
            doThrow(new SqliteDatabaseException("tick write failure")).when(f.repository).upsertAnimals(anyList());
            doThrow(new SqliteDatabaseException("snapshot write failure")).when(f.repository).upsertSnapshots(anyMap(), anyLong());
            assertDoesNotThrow(() -> f.server.getScheduler().performTicks(1200));
            when(f.plugin.getHusbandryRepository()).thenReturn(null); f.server.getScheduler().performTicks(1200); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
            HusbandryEntities.clearLoaded(); f.server.getScheduler().performTicks(1200); HusbandryTickTask.stop(); HusbandryTickTask.stop();
            BreedingBuddies.plugin = null; HusbandryTickTask.start(); BreedingBuddies.plugin = f.plugin;
        }
    }

    @Test void locationMemoryFloorsCoordinatesAndIgnoresUnavailableEntities() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); cow.teleport(new Location(f.world, -1.2, 63.9, 2.7)); HusbandryLocation.remember(animal, cow);
        assertEquals("farm", animal.world()); assertEquals(-2, animal.x()); assertEquals(63, animal.y()); assertEquals(2, animal.z());
        HusbandryLocation.remember(null, cow); HusbandryLocation.remember(animal, null); HusbandryLocation.remember(animal, mock(Entity.class)); assertEquals(-2, animal.x());
        HusbandryEntities.applyPersistFlags(null); HusbandryEntities.stampManaged(null); HusbandryEntities.putLoaded(null); assertFalse(HusbandryEntities.isManaged(null)); assertEquals("Animal", HusbandryEntities.displayName(null)); assertEquals("Skeleton Horse", HusbandryEntities.displayName(EntityType.SKELETON_HORSE));
        HusbandryEntities.untrack(animal.uuid()); assertTrue(HusbandryEntities.lookup(animal.uuid()).isPresent()); BreedingBuddies.plugin = null; assertNull(HusbandryEntities.repository()); assertTrue(HusbandryEntities.lookup(UUID.randomUUID()).isEmpty()); BreedingBuddies.plugin = f.plugin;
    }

    @Test void naturalProductsWaitForMaturityHappinessAndTheirOwnTimers() throws Exception {
        f.config.set("species.CHICKEN.egg", "vanilla"); f.config.set("egg-timer", "20s"); f.config.set("species.SHEEP.shed.drops.common", List.of(Map.of("path", "wool"))); f.config.set("shed-timer", "30s"); f.config.set("shed-chance", 1); f.reload();
        assertTrue(HusbandryEggs.isVanillaEgg(Material.EGG)); assertTrue(HusbandryEggs.isVanillaEgg(Material.BLUE_EGG)); assertTrue(HusbandryEggs.isVanillaEgg(Material.BROWN_EGG)); assertFalse(HusbandryEggs.isVanillaEgg(Material.CHICKEN));
        Chicken chicken = f.spawn(Chicken.class); HusbandryAnimal hen = f.record(chicken); Sheep sheep = f.spawn(Sheep.class); HusbandryAnimal wool = f.record(sheep); long now = 1_000_000L;
        HusbandryEggs.tryLay(null, hen, now); HusbandryEggs.tryLay(chicken, null, now); HusbandryShed.tryShed(null, wool, now); HusbandryShed.tryShed(sheep, null, now);
        hen.setMatureAt(now + 1); wool.setMatureAt(now + 1); HusbandryEggs.tryLay(chicken, hen, now); HusbandryShed.tryShed(sheep, wool, now); assertNull(hen.eggReadyAt()); assertNull(wool.shedReadyAt());
        hen.setMatureAt(null); wool.setMatureAt(null); hen.setHungrySince(1L); wool.setDirtySince(1L); HusbandryEggs.tryLay(chicken, hen, now); HusbandryShed.tryShed(sheep, wool, now); assertNull(hen.eggReadyAt()); assertNull(wool.shedReadyAt()); hen.setHungrySince(null); wool.setDirtySince(null);
        hen.setType("NOT_A_MINECRAFT_MOB"); wool.setType("NOT_A_MINECRAFT_MOB"); HusbandryEggs.tryLay(chicken, hen, now); HusbandryShed.tryShed(sheep, wool, now); hen.setType("PIG"); wool.setType("PIG"); HusbandryEggs.tryLay(chicken, hen, now); HusbandryShed.tryShed(sheep, wool, now); hen.setType("COW"); wool.setType("COW"); HusbandryEggs.tryLay(chicken, hen, now); HusbandryShed.tryShed(sheep, wool, now); hen.setType("CHICKEN"); wool.setType("SHEEP");
        HusbandryEggs.tryLay(chicken, hen, now); assertEquals(now + 20_000, hen.eggReadyAt()); assertEquals(1, itemCount(Material.EGG)); HusbandryEggs.tryLay(chicken, hen, now + 1); assertEquals(1, itemCount(Material.EGG));
        try (MockedStatic<HusbandryDropRoller> drops = mockStatic(HusbandryDropRoller.class)) {
            drops.when(() -> HusbandryDropRoller.rollShedDrops(eq(wool), any())).thenReturn(Arrays.asList(new ItemStack(Material.WHITE_WOOL, 2), null));
            HusbandryShed.tryShed(sheep, wool, now); assertEquals(now + 30_000, wool.shedReadyAt()); assertEquals(2, itemCount(Material.WHITE_WOOL)); HusbandryShed.tryShed(sheep, wool, now + 1); assertEquals(2, itemCount(Material.WHITE_WOOL));
            f.config.set("shed-chance", 0); f.reload(); HusbandryShed.tryShed(sheep, wool, now + 30_000); assertEquals(now + 60_000, wool.shedReadyAt()); assertEquals(2, itemCount(Material.WHITE_WOOL));
        }
        try (MockedStatic<HusbandryHarvest> harvest = mockStatic(HusbandryHarvest.class, CALLS_REAL_METHODS)) {
            harvest.when(() -> HusbandryHarvest.buildFood(hen, "food(egg)")).thenReturn(new ItemStack(Material.BROWN_EGG));
            f.config.set("species.CHICKEN.egg", "food(egg)"); f.reload(); HusbandryEggs.tryLay(chicken, hen, now + 20_000); assertEquals(1, itemCount(Material.BROWN_EGG));
            harvest.when(() -> HusbandryHarvest.buildTlibs("custom_egg", 1)).thenReturn(null); f.config.set("species.CHICKEN.egg", "custom_egg"); f.reload(); HusbandryEggs.tryLay(chicken, hen, now + 40_000); assertEquals(now + 60_000, hen.eggReadyAt());
        }
    }

    private int itemCount(Material material) { return f.world.getEntitiesByClass(Item.class).stream().map(Item::getItemStack).filter(item -> item.getType() == material).mapToInt(ItemStack::getAmount).sum(); }

    static HusbandryAnimal simulationAnimal() { HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Simulated"); animal.setLastProcessedAt(0); animal.setAfflictionAt(100); return animal; }

    /** Real Bukkit values and SQLite; only the external item API and unimplemented despawn flag are adapters. */
    static final class Farm implements AutoCloseable {
        final Path directory;
        final ServerMock server;
        final WorldMock world;
        final PlayerMock player, neighbour;
        final BreedingBuddies plugin, previous;
        final HusbandryRepository repository;
        final YamlConfiguration config = new YamlConfiguration();
        final HusbandryLoaderCoverageTest.ConfigSnapshot snapshot;
        final MockedStatic<TLibs> tlibs;
        final CookingApiStub cooking;
        final net.tfminecraft.breedingbuddies.testsupport.TestCharacters characters;
        Farm(Path directory) throws Exception {
            this.directory = directory; snapshot = new HusbandryLoaderCoverageTest.ConfigSnapshot(); previous = BreedingBuddies.plugin;
            server = MockBukkit.mock(); world = addWorld("farm"); player = server.addPlayer("Farmer"); neighbour = server.addPlayer("Neighbour"); player.teleport(new Location(world, 0, 65, 0)); neighbour.teleport(new Location(world, 1, 65, 0));
            plugin = mock(BreedingBuddies.class); when(plugin.getName()).thenReturn("BreedingBuddies"); when(plugin.namespace()).thenReturn("breedingbuddies"); when(plugin.isEnabled()).thenReturn(true); BreedingBuddies.plugin = plugin;
            characters = new net.tfminecraft.breedingbuddies.testsupport.TestCharacters(plugin);
            characters.activate(player, player.getUniqueId()); characters.activate(neighbour, neighbour.getUniqueId());
            when(characters.provider.info(any())).thenAnswer(call -> {
                UUID id = call.getArgument(0); Player online = server.getPlayer(id);
                return online == null ? Optional.empty() : Optional.of(new net.tfminecraft.tlibs.character.CharacterInfo(
                        new net.tfminecraft.tlibs.character.CharacterRef(id, id), online.getName(),
                        net.tfminecraft.tlibs.character.CharacterStatus.ALIVE, null, Set.of(), 0, null));
            });
            repository = spy(HusbandryRepository.open(directory.resolve("husbandry.db").toFile())); when(plugin.getHusbandryRepository()).thenReturn(repository); HusbandryEntities.clearLoaded();
            config.loadFromString("""
                    max-animals: 2
                    initial-genetic-max: 0
                    max-genetics: 1000
                    care-max: 100
                    items: {inspect: inspect, tame: tame, co-own: share, feed: feed, glove: glove, neuter: neuter}
                    species: {COW: {milk: true}, HORSE: {}, SHEEP: {}}
                    mounts:
                      HORSE: {min-health: 25, max-health: 25, min-speed: 0.2, max-speed: 0.2, min-jump: 0.6, max-jump: 0.6}
                    """); reload();
            ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS); tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api); cooking = new CookingApiStub();
            when(api.getChecker().checkItemWithPath(any(), anyString())).thenAnswer(call -> {
                ItemStack item = call.getArgument(0); String path = call.getArgument(1); if (item == null) return false;
                return switch (path) {
                    case "inspect" -> item.getType() == Material.CLOCK;
                    case "tame" -> item.getType() == Material.NAME_TAG;
                    case "share" -> item.getType() == Material.PAPER;
                    case "feed" -> item.getType() == Material.WHEAT;
                    case "glove" -> item.getType() == Material.LEATHER;
                    case "neuter" -> item.getType() == Material.SHEARS;
                    case "bucket" -> item.getType() == Material.BUCKET;
                    default -> false;
                };
            });
        }
        void reload() throws Exception { File file = directory.resolve("husbandry.yml").toFile(); config.save(file); new HusbandryLoader().load(file); }
        WorldMock addWorld(String name) { WorldMock added = new DiskWorld(directory.resolve(name).toFile()); added.setName(name); server.addWorld(added); return added; }
        <T extends Entity> T spawn(Class<T> type) {
            Map<Class<?>, Class<? extends Entity>> types = Map.of(Cow.class, CowMock.class, Horse.class, HorseMock.class, Pig.class, PigMock.class, Sheep.class, SheepMock.class, Donkey.class, DonkeyMock.class, Chicken.class, ChickenMock.class, ItemDisplay.class, ItemDisplayMock.class, TextDisplay.class, TextDisplayMock.class);
            Class<? extends Entity> mockType = Objects.requireNonNull(types.get(type), "Unsupported fixture entity " + type);
            T raw = type.cast(world.spawn(new Location(world, 0, 65, 0), mockType));
            if (!(raw instanceof LivingEntity)) return raw;
            T entity = spy(raw); LivingEntity living = (LivingEntity) entity; boolean[] despawns = {true};
            doAnswer(call -> { despawns[0] = call.getArgument(0); return null; }).when(living).setRemoveWhenFarAway(anyBoolean()); doAnswer(call -> despawns[0]).when(living).getRemoveWhenFarAway();
            raw.remove(); server.registerEntity((EntityMock) entity); return entity;
        }
        HusbandryAnimal record(LivingEntity entity) { HusbandryAnimal animal = new HusbandryAnimal(entity.getUniqueId(), entity.getType().name(), HusbandryEntities.displayName(entity.getType())); animal.setLastProcessedAt(System.currentTimeMillis()); animal.setAfflictionAt(100); animal.setStatsRevision(HusbandryConfig.statsRevision()); repository.upsertAnimal(animal); HusbandryEntities.putLoaded(animal); return animal; }
        HusbandryAnimal offline(String type, String name) { HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), type, name); animal.setLastProcessedAt(System.currentTimeMillis()); animal.setUnloadedAt(1L); animal.setLastLocation("farm", 0, 65, 0); repository.upsertAnimal(animal); return animal; }
        HusbandryAnimal ownedOffline(String name) { HusbandryAnimal animal = offline("COW", name); own(animal, player); return animal; }
        void own(HusbandryAnimal animal, PlayerMock owner) { animal.setState(HusbandryAnimalState.OWNED); repository.upsertAnimal(animal); repository.upsertOwner(new HusbandryOwner(animal.uuid(), owner.getUniqueId(), owner.getUniqueId(), "owner")); }
        @Override public void close() throws Exception {
            for (HusbandryAnimal animal : repository.listOwnedAnimals()) HusbandryLocator.markFound(animal.uuid());
            HusbandryTickTask.stop(); HusbandryEntities.clearLoaded(); repository.close(); characters.close(); MockBukkit.unmock(); tlibs.close(); cooking.close(); BreedingBuddies.plugin = previous; snapshot.close();
        }
    }

    static final class DiskWorld extends WorldMock {
        private final File folder;
        DiskWorld(File folder) { this.folder = folder; }
        @Override public File getWorldFolder() { return folder; }
    }

    /** Scheduling and disk scan are boundaries; applying their results still exercises the public scan workflow. */
    static final class Scan implements AutoCloseable {
        final Deque<Runnable> async = new ArrayDeque<>(), sync = new ArrayDeque<>();
        List<HusbandryEntityScan.WorldDir> worlds;
        final MockedStatic<Bukkit> bukkit;
        final MockedStatic<HusbandryEntityScan> scanner;
        Scan(HusbandryEntityScan.Result result) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> { async.add(call.getArgument(1)); return null; });
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> { sync.add(call.getArgument(1)); return null; });
            bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            scanner = mockStatic(HusbandryEntityScan.class); scanner.when(() -> HusbandryEntityScan.scan(anyList(), any(File.class), anySet())).thenAnswer(call -> { worlds = call.getArgument(0); return result; });
        }
        void background() { async.removeFirst().run(); }
        void apply() { sync.removeFirst().run(); }
        @Override public void close() { scanner.close(); bukkit.close(); }
    }
}
