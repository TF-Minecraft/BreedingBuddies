package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.listeners.DayChangeListener;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PluginDataTest extends PluginTestBase {

    private static FarmAnimal detailedAnimal(String name) {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), name, new HashSet<>());
        animal.setStableNeeded(false);
        animal.setFriendshipPoints(1200);
        animal.setGeneticPoints(340);
        animal.setState(AnimalStates.SAD);
        animal.setFed(true);
        animal.setCared(true);
        animal.setSleptInStable(false);
        animal.setSpaceNeeded(true);
        animal.setWaterNeeded(true);
        animal.setDaysOut(2);
        animal.setLastRewardCollected(new Date(1_700_000_000_000L));
        animal.setLastUpdated(new Date(1_700_000_500_000L));
        return animal;
    }

    private static void assertSameDetails(FarmAnimal expected, FarmAnimal actual) {
        assertEquals(expected.getUuid(), actual.getUuid());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.isStableNeeded(), actual.isStableNeeded());
        assertEquals(expected.getFriendshipPoints(), actual.getFriendshipPoints());
        assertEquals(expected.getGeneticPoints(), actual.getGeneticPoints());
        assertEquals(expected.getState(), actual.getState());
        assertEquals(expected.getFed(), actual.getFed());
        assertEquals(expected.getCared(), actual.getCared());
        assertEquals(expected.isSleptInStable(), actual.isSleptInStable());
        assertEquals(expected.isSpaceNeeded(), actual.isSpaceNeeded());
        assertEquals(expected.isWaterNeeded(), actual.isWaterNeeded());
        assertEquals(expected.getDaysOut(), actual.getDaysOut());
        assertEquals(expected.getLastRewardCollected(), actual.getLastRewardCollected());
        assertEquals(expected.getLastUpdated(), actual.getLastUpdated());
    }

    @Test
    void onlinePlayersAnimalsSurviveASaveAndReload() {
        PlayerMock alice = player();
        PlayerMock bob = player();
        FarmAnimal shared = detailedAnimal("Daisy");
        FarmAnimal fresh = new FarmAnimal(UUID.randomUUID(), "Clover", new HashSet<>());
        fresh.setState(AnimalStates.HAPPY);
        OwnershipManager.registerOwnership(alice.getUniqueId(), shared);
        OwnershipManager.registerOwnership(bob.getUniqueId(), shared);
        OwnershipManager.registerOwnership(bob.getUniqueId(), fresh);

        PluginData.saveAllData(false);
        PluginData.getPlayerAnimals().clear();
        PluginData.loadAllData();

        FarmAnimal aliceDaisy = OwnershipManager.getAnimal(shared.getUuid(), alice.getUniqueId());
        FarmAnimal bobDaisy = OwnershipManager.getAnimal(shared.getUuid(), bob.getUniqueId());
        assertSameDetails(shared, aliceDaisy);
        assertSame(aliceDaisy, bobDaisy, "co-owners share one loaded record");
        assertEquals(Set.of(alice.getUniqueId(), bob.getUniqueId()), aliceDaisy.getOwnersUuids());
        FarmAnimal loadedClover = OwnershipManager.getAnimal(fresh.getUuid(), bob.getUniqueId());
        assertNull(loadedClover.getLastRewardCollected());
        assertNull(loadedClover.getLastUpdated());
        assertEquals(AnimalStates.HAPPY, loadedClover.getState());
    }

    @Test
    void offlinePlayersAreNotWrittenByTheRegularSave() {
        UUID offline = UUID.randomUUID();
        OwnershipManager.registerOwnership(offline, detailedAnimal("Daisy"));

        PluginData.saveAllData(false);

        assertFalse(Files.exists(playerFile(offline)));
    }

    @Test
    void aPlayerListedInTheFileIsAddedBackAsOwner() throws Exception {
        UUID player = UUID.randomUUID();
        FarmAnimal daisy = detailedAnimal("Daisy");
        daisy.getOwnersUuids().add(UUID.randomUUID());
        PluginData.savePlayerData(player, Set.of(daisy));

        PluginData.loadAllData();

        assertTrue(OwnershipManager.getAnimal(daisy.getUuid(), player).getOwnersUuids().contains(player));
    }

    @Test
    void loadingAPlayerWithoutSavedDataLeavesTheirAnimalsAlone() {
        UUID player = UUID.randomUUID();
        FarmAnimal daisy = detailedAnimal("Daisy");
        OwnershipManager.registerOwnership(player, daisy);

        PluginData.loadPlayerData(player, new java.util.HashMap<>());

        assertEquals(Set.of(daisy), OwnershipManager.getAnimals(player));
    }

    @Test
    void unownedAnimalsSurviveASaveAndReload() {
        FarmAnimal stray = detailedAnimal("???");
        stray.getOwnersUuids().add(UUID.randomUUID());
        FarmAnimal plain = new FarmAnimal(UUID.randomUUID(), "???", new HashSet<>());
        plain.setState(AnimalStates.UNOWNED);
        UnownedAnimalsManager.addUnownedAnimal(stray);
        UnownedAnimalsManager.addUnownedAnimal(plain);

        PluginData.saveAllData(true);
        PluginData.getUnownedAnimals().clear();
        PluginData.loadAllData();

        assertSameDetails(stray, UnownedAnimalsManager.getAnimal(stray.getUuid()));
        assertEquals(stray.getOwnersUuids(), UnownedAnimalsManager.getAnimal(stray.getUuid()).getOwnersUuids());
        assertSameDetails(plain, UnownedAnimalsManager.getAnimal(plain.getUuid()));
    }

    @Test
    void stableAreasSurviveASaveAndReload() {
        ChunkManager.addChunk(world.getChunkAt(0, 0));
        ChunkManager.addChunk(world.getChunkAt(0, 1));
        ChunkManager.addChunk(world.getChunkAt(7, 7));
        PluginData.getChunkAreas().get(0).setDaysAbandoned(3);
        int abandoned0 = PluginData.getChunkAreas().get(0).getDaysAbandoned();
        Set<org.bukkit.Chunk> chunks0 = Set.copyOf(PluginData.getChunkAreas().get(0).getChunks());

        PluginData.saveAllData(false);
        PluginData.getChunkAreas().clear();
        PluginData.loadAllData();

        assertEquals(2, PluginData.getChunkAreas().size());
        ChunkArea reloaded = ChunkArea.findAreaForChunk(PluginData.getChunkAreas(), chunks0.iterator().next());
        assertEquals(chunks0, reloaded.getChunks());
        assertEquals(abandoned0, reloaded.getDaysAbandoned());
    }

    @Test
    void stableChunksInMissingWorldsAreSkipped() throws Exception {
        Files.createDirectories(DATA_DIR);
        Files.writeString(DATA_DIR.resolve("stablechunks.json"),
                "[{\"daysAbandoned\":1,\"chunks\":[{\"world\":\"gone\",\"x\":1,\"z\":2},{\"world\":\"world\",\"x\":3,\"z\":4}]}]");
        PrintStream original = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true));
        try {
            PluginData.loadStableChunks();
        } finally {
            System.setErr(original);
        }

        assertEquals(Set.of(world.getChunkAt(3, 4)), PluginData.getChunkAreas().get(0).getChunks());
        assertEquals(1, PluginData.getChunkAreas().get(0).getDaysAbandoned());
        assertTrue(err.toString().contains("World gone not found."));
    }

    @Test
    void savingWhileADayChangeRunsIsLeftToTheDayChange() throws Exception {
        PlayerMock alice = player();
        OwnershipManager.registerOwnership(alice.getUniqueId(), detailedAnimal("Daisy"));
        setStatic(DayChangeListener.class, "isChangingDay", true);

        PluginData.saveAllData(false);
        assertFalse(Files.exists(playerFile(alice.getUniqueId())));

        PluginData.saveAllData(true);
        assertTrue(Files.exists(playerFile(alice.getUniqueId())));
        setStatic(DayChangeListener.class, "isChangingDay", false);
    }

    @Test
    void nothingIsLoadedWhenNoDataExists() {
        PluginData.loadAllData();

        assertTrue(PluginData.getPlayerAnimals().isEmpty());
        assertTrue(PluginData.getUnownedAnimals().isEmpty());
        assertTrue(PluginData.getChunkAreas().isEmpty());
    }

    @Test
    void strayFilesInThePlayerFolderAreIgnoredOrReported() throws Exception {
        Files.createDirectories(DATA_DIR.resolve("PlayerData").resolve("nested"));
        Files.writeString(DATA_DIR.resolve("PlayerData").resolve("notes.txt"), "hello");
        Files.writeString(DATA_DIR.resolve("PlayerData").resolve("not-a-uuid.json"), "{}");

        try (LogCapture log = new LogCapture(Bukkit.getLogger())) {
            PluginData.loadAllData();
            assertTrue(log.contains("Invalid player data file: not-a-uuid.json"));
            assertFalse(log.contains("notes.txt"));
        }
        assertTrue(PluginData.getPlayerAnimals().isEmpty());
    }

    @Test
    void corruptFilesDoNotReplaceWhatIsAlreadyLoaded() throws Exception {
        ChunkManager.addChunk(world.getChunkAt(0, 0));
        UUID player = UUID.randomUUID();
        Files.createDirectories(DATA_DIR.resolve("PlayerData"));
        Files.writeString(DATA_DIR.resolve("stablechunks.json"), "{not json");
        Files.writeString(DATA_DIR.resolve("unownedanimals.json"), "{not json");
        Files.writeString(playerFile(player), "{not json");

        assertDoesNotThrow(PluginData::loadAllData);

        assertEquals(1, PluginData.getChunkAreas().size());
        assertTrue(PluginData.getUnownedAnimals().isEmpty());
        assertFalse(PluginData.getPlayerAnimals().containsKey(player));
    }

    @Test
    void anUnwritableDataFolderDoesNotStopTheServer() throws Exception {
        PlayerMock alice = player();
        OwnershipManager.registerOwnership(alice.getUniqueId(), detailedAnimal("Daisy"));
        UnownedAnimalsManager.addUnownedAnimal(detailedAnimal("???"));
        ChunkManager.addChunk(world.getChunkAt(0, 0));
        Files.createDirectories(DATA_DIR.getParent());
        Files.writeString(DATA_DIR, "a file where the folder should be");

        assertDoesNotThrow(() -> PluginData.saveAllData(true));

        assertTrue(Files.isRegularFile(DATA_DIR));
    }
}
