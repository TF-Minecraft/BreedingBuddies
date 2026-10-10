package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.AnimalStates;
import net.tfminecraft.breedingbuddies.ChunkArea;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.OwnershipManager;
import net.tfminecraft.breedingbuddies.PluginData;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.UnownedAnimalsManager;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DayChangeListenerTest extends PluginTestBase {
    private final ByteArrayOutputStream console = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PlayerMock alice;

    @BeforeEach
    void captureConsole() {
        originalOut = System.out;
        System.setOut(new PrintStream(console, true));
        alice = player();
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(originalOut);
    }

    private void dayChange() throws InterruptedException {
        server.getPluginManager().callEvent(new DayChangeEvent());
        awaitDayChange();
    }

    private FarmAnimal ownedCow(boolean fedAndCared) {
        FarmAnimal animal = own(alice, spawn(Cow.class));
        animal.setFriendshipPoints(500);
        animal.setFed(fedAndCared);
        animal.setCared(fedAndCared);
        return animal;
    }

    private Entity entityOf(FarmAnimal animal) {
        return server.getEntity(animal.getUuid());
    }

    private long storedLastDayChange() {
        return YamlConfiguration.loadConfiguration(plugin.getNumbersFile()).getLong("lastDayChangeDate");
    }

    @Test
    void animalsSleepingInAWateredRoomyStableStayHappy() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        daisy.setDaysOut(2);
        stable(fakeChunk(0, 0, true, entityOf(daisy)));
        long before = storedLastDayChange() - 3600;
        YamlConfiguration numbers = YamlConfiguration.loadConfiguration(plugin.getNumbersFile());
        numbers.set("lastDayChangeDate", before);
        DayChangeListener.setNumbersConfig(numbers);

        dayChange();

        assertEquals(AnimalStates.HAPPY, daisy.getState());
        assertFalse(daisy.getFed());
        assertFalse(daisy.getCared());
        assertEquals(400, daisy.getFriendshipPoints(), "a day costs the configured care and feeding loss");
        assertTrue(daisy.isSleptInStable());
        assertFalse(daisy.isWaterNeeded());
        assertFalse(daisy.isSpaceNeeded());
        assertEquals(0, daisy.getDaysOut());
        assertTrue(Files.exists(playerFile(alice.getUniqueId())), "the day change saves data");
        assertTrue(storedLastDayChange() > before);
        assertFalse(DayChangeListener.isIsChangingDay());
    }

    @Test
    void theDayChangeRunsOnTheServerThreadOnTheNextTick() {
        FarmAnimal daisy = ownedCow(true);
        Chunk home = fakeChunk(0, 0, true);
        List<Boolean> readOnServerThread = new ArrayList<>();
        when(home.getEntities()).thenAnswer(call -> {
            readOnServerThread.add(Bukkit.isPrimaryThread());
            return new Entity[] {entityOf(daisy)};
        });
        stable(home);

        server.getPluginManager().callEvent(new DayChangeEvent());

        assertTrue(DayChangeListener.isIsChangingDay());
        assertTrue(daisy.getFed(), "nothing changes before the task runs");

        awaitDayChange();

        assertFalse(daisy.getFed());
        assertFalse(readOnServerThread.isEmpty());
        assertFalse(readOnServerThread.contains(false), "chunk entities are read on the server thread");
    }

    @Test
    void stablesWithoutWaterOrRoomLeaveAnimalsSad() throws Exception {
        FarmAnimal thirsty = ownedCow(true);
        stable(fakeChunk(0, 0, false, entityOf(thirsty)));
        Numbers.maxAnimalsPerChunk = 0;
        FarmAnimal crowded = ownedCow(true);
        stable(fakeChunk(5, 5, true, entityOf(crowded)));

        dayChange();

        assertEquals(AnimalStates.SAD, thirsty.getState());
        assertTrue(thirsty.isWaterNeeded());
        assertEquals(AnimalStates.SAD, crowded.getState());
        assertTrue(crowded.isSpaceNeeded());
    }

    @Test
    void animalsOutsideAnyStableCountANightOutButMountsDoNot() throws Exception {
        FarmAnimal stray = ownedCow(true);
        FarmAnimal mount = own(alice, spawn(Horse.class));
        mount.setFed(true);
        mount.setCared(true);

        dayChange();

        assertEquals(AnimalStates.SAD, stray.getState());
        assertFalse(stray.isSleptInStable());
        assertEquals(1, stray.getDaysOut());
        assertEquals(AnimalStates.HAPPY, mount.getState());
        assertTrue(mount.isSleptInStable());
        assertEquals(0, mount.getDaysOut());
    }

    @Test
    void animalsLeftOutTooLongEscapeTheirOwners() throws Exception {
        FarmAnimal stray = ownedCow(false);
        stray.setDaysOut(Numbers.maxIrlDaysOut);

        dayChange();

        assertEquals(AnimalStates.ESCAPED, stray.getState());
        assertFalse(OwnershipManager.hasOwner(stray.getUuid()));
        assertTrue(OwnershipManager.getAnimals(alice.getUniqueId()).isEmpty());
        assertSame(stray, UnownedAnimalsManager.getAnimal(stray.getUuid()));
    }

    @Test
    void stablesWithoutOwnedAnimalsCountAsAbandoned() throws Exception {
        Cow wild = spawn(Cow.class);
        ChunkArea empty = stable(fakeChunk(0, 0, true, wild));
        FarmAnimal daisy = ownedCow(true);
        ChunkArea used = stable(fakeChunk(5, 5, true, entityOf(daisy)));

        dayChange();

        assertEquals(1, empty.getDaysAbandoned());
        assertEquals(0, used.getDaysAbandoned());
    }

    @Test
    void stablesAbandonedTooLongAreReleased() throws Exception {
        ChunkArea expired = stable(fakeChunk(0, 0, true));
        expired.setDaysAbandoned(Numbers.maxIrlDaysChunkAbandoned + 1);
        ChunkArea kept = stable(fakeChunk(5, 5, true));
        kept.setDaysAbandoned(Numbers.maxIrlDaysChunkAbandoned);
        // Another day change already running means no second pass is started.
        setStatic(DayChangeListener.class, "isChangingDay", true);

        server.getPluginManager().callEvent(new DayChangeEvent());
        setStatic(DayChangeListener.class, "isChangingDay", false);

        assertEquals(java.util.List.of(kept), PluginData.getChunkAreas());
        assertEquals(Numbers.maxIrlDaysChunkAbandoned, kept.getDaysAbandoned());
    }

    @Test
    void aChunkThatFailsMidwayIsLoggedAndOthersStillChange() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        Chunk broken = fakeChunk(4, 5, true);
        when(broken.getEntities()).thenReturn(new Entity[0]).thenThrow(new IllegalStateException("chunk unloaded"));
        stable(broken);
        stable(fakeChunk(9, 9, true, entityOf(daisy)));

        try (LogCapture log = new LogCapture(plugin.getLogger())) {
            dayChange();
            assertTrue(log.contains("Error processing chunk 4,5: chunk unloaded"));
        }

        assertFalse(broken.isForceLoaded(), "the failed chunk is released");
        assertEquals(AnimalStates.HAPPY, daisy.getState());
        assertTrue(daisy.isSleptInStable());
    }

    @Test
    void anAnimalThatFailsIsLoggedAndTheRestOfTheChunkStillChanges() throws Exception {
        FarmAnimal broken = spy(new FarmAnimal(spawn(Cow.class).getUniqueId(), "Broken", alice.getUniqueId(), null));
        doThrow(new IllegalStateException("bad record")).doCallRealMethod()
                .when(broken).changeDay(anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
        OwnershipManager.registerOwnership(alice.getUniqueId(), broken);
        FarmAnimal daisy = ownedCow(true);
        stable(fakeChunk(0, 0, true, entityOf(broken), entityOf(daisy)));

        try (LogCapture log = new LogCapture(plugin.getLogger())) {
            dayChange();
            assertTrue(log.contains("Error processing animal " + broken.getUuid() + ": bad record"));
        }

        assertEquals(AnimalStates.HAPPY, daisy.getState());
        assertTrue(daisy.isSleptInStable());
        assertFalse(console.toString().contains("Error on DayChange"), "the rest of the day change completed");
    }

    @Test
    void aFailedDayChangeIsReportedAndDoesNotBlockTheNextOne() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        Chunk broken = fakeChunk(0, 0, true);
        when(broken.isForceLoaded()).thenThrow(new IllegalStateException("world closed"));
        ChunkArea brokenArea = stable(broken);

        dayChange();

        assertTrue(console.toString().contains("Error on DayChange: java.lang.IllegalStateException: world closed"));
        assertFalse(DayChangeListener.isIsChangingDay());
        assertTrue(daisy.getFed(), "nothing changed");

        PluginData.getChunkAreas().remove(brokenArea);
        dayChange();
        assertFalse(daisy.getFed());
    }

    @Test
    void aMissedDayIsAppliedWhenAnOwnerInteracts() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        Chunk home = entityOf(daisy).getLocation().getChunk();
        stable(home, fakeChunk(1, 0, true));
        FarmAnimal other = ownedCow(false);
        setLastDayChange(2);

        DayChangeListener.changeDayOnInteract(entityOf(daisy), daisy);
        awaitDayChange();

        assertEquals(AnimalStates.HAPPY, daisy.getState());
        assertEquals(300, daisy.getFriendshipPoints(), "two missed days cost twice the daily loss, once");
        assertEquals(0, daisy.getDaysOut(), "it slept in its stable");
        assertEquals(AnimalStates.SAD, other.getState());
        assertEquals(300, other.getFriendshipPoints());
        assertTrue(Math.abs(Instant.now().getEpochSecond() - storedLastDayChange()) < 60);
    }

    @Test
    void interactionsOnTheSameDayChangeNothing() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        setLastDayChange(0);

        DayChangeListener.changeDayOnInteract(entityOf(daisy), daisy);
        awaitDayChange();

        assertTrue(daisy.getFed());
        assertEquals(500, daisy.getFriendshipPoints());
    }

    @Test
    void anInteractedAnimalAwayFromItsStableHadANightOut() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        setLastDayChange(1);

        DayChangeListener.changeDayOnInteract(entityOf(daisy), daisy);
        awaitDayChange();

        assertEquals(AnimalStates.SAD, daisy.getState());
        assertFalse(daisy.isSleptInStable());
        assertEquals(1, daisy.getDaysOut());
        assertEquals(400, daisy.getFriendshipPoints());
    }

    @Test
    void anInteractedMountNeedsNoStable() throws Exception {
        FarmAnimal mount = own(alice, spawn(Horse.class));
        mount.setFed(true);
        mount.setCared(true);
        setLastDayChange(1);

        DayChangeListener.changeDayOnInteract(entityOf(mount), mount);

        assertEquals(AnimalStates.HAPPY, mount.getState(), "applied before the background pass");
        assertTrue(mount.isSleptInStable());
        awaitDayChange();
        assertEquals(0, mount.getDaysOut());
    }

    @Test
    void aFarmReportCatchesUpOnMissedDaysStraightAway() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        setLastDayChange(3);

        DayChangeListener.changeDayOnReport();

        assertFalse(daisy.getFed());
        assertEquals(200, daisy.getFriendshipPoints());
        assertTrue(Files.exists(playerFile(alice.getUniqueId())));
        assertFalse(DayChangeListener.isIsChangingDay());
        assertTrue(Math.abs(Instant.now().getEpochSecond() - storedLastDayChange()) < 60);
    }

    @Test
    void aFarmReportOnTheSameDayOrDuringAChangeDoesNothing() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        setLastDayChange(0);

        DayChangeListener.changeDayOnReport();
        assertTrue(daisy.getFed());
        assertFalse(DayChangeListener.isIsChangingDay());

        setLastDayChange(2);
        setStatic(DayChangeListener.class, "isChangingDay", true);
        DayChangeListener.changeDayOnReport();
        assertTrue(daisy.getFed());
        assertTrue(DayChangeListener.isIsChangingDay());
        setStatic(DayChangeListener.class, "isChangingDay", false);
    }

    @Test
    void aFailedFarmReportCatchUpIsReportedAndReleased() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        Chunk broken = fakeChunk(0, 0, true);
        when(broken.isForceLoaded()).thenThrow(new IllegalStateException("world closed"));
        stable(broken);
        setLastDayChange(1);

        DayChangeListener.changeDayOnReport();

        assertTrue(console.toString().contains("Error on DayChange: java.lang.IllegalStateException: world closed"));
        assertFalse(DayChangeListener.isIsChangingDay());
        assertTrue(daisy.getFed());
    }

    @Test
    void anUnsavableConfigStillCompletesTheDayChange() throws Exception {
        FarmAnimal daisy = ownedCow(true);
        setLastDayChange(1);
        Files.delete(plugin.getNumbersFile().toPath());
        Files.createDirectory(plugin.getNumbersFile().toPath());

        DayChangeListener.changeDayOnReport();

        assertFalse(daisy.getFed());
        assertTrue(console.toString().contains("Error saving lastDayChangeDate on config: "));
    }

    @Test
    void wholeDaysAreCountedBetweenDates() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 21, 0);

        assertEquals(0, DayChangeListener.daysBetween(start, start.plusHours(23)));
        assertEquals(2, DayChangeListener.daysBetween(start, start.plusDays(2).plusHours(5)));
    }
}
