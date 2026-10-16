package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

class HusbandryMaintenanceRegressionTest {
    @TempDir Path directory;

    enum SnapshotFailure { EXCEPTION, NULL_ENTITY, WRONG_UUID }

    @ParameterizedTest
    @EnumSource(SnapshotFailure.class)
    void unrestorableSnapshotsKeepAnimalOwnersAndRawBackup(SnapshotFailure failure) throws Exception {
        try (var farm = new HusbandryRuntimeCoverageTest.Farm(directory)) {
            var repo = farm.repository;
            HusbandryAnimal animal = farm.ownedOffline("Recoverable animal");
            repo.upsertOwner(new HusbandryOwner(animal.uuid(), farm.neighbour.getUniqueId(),
                    farm.neighbour.getUniqueId(), "coowner"));
            byte[] snapshot = {0, 7, -1};
            repo.upsertSnapshots(Map.of(animal.uuid(), snapshot), 73);
            var connection = repo.ownershipDatabase().getConnection();
            UUID death = UUID.randomUUID();
            var before = heldState(connection, animal.uuid(), death);
            UnsafeValues unsafe = mock(UnsafeValues.class);
            Entity wrong = mock(Entity.class);
            when(wrong.getUniqueId()).thenReturn(UUID.randomUUID());
            when(unsafe.deserializeEntity(any(byte[].class), eq(farm.world), eq(true))).thenAnswer(call -> {
                return switch (failure) {
                    case EXCEPTION -> throw new IllegalStateException("Cannot deserialize snapshot");
                    case NULL_ENTITY -> null;
                    case WRONG_UUID -> wrong;
                };
            });
            try (var scan = new HusbandryRuntimeCoverageTest.Scan(
                    new HusbandryEntityScan.Result(Map.of(), Set.of(), true))) {
                scan.bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
                HusbandryLocator.scanUnloaded(); scan.background(); scan.apply();
            }

            assertEquals(before, heldState(connection, animal.uuid(), death));
            assertArrayEquals(snapshot, repo.getSnapshot(animal.uuid()).orElseThrow().data());
            assertTrue(HusbandryLocator.isMissing(animal.uuid()));
            assertEquals(2, repo.listOwners(animal.uuid()).size());
            verify(repo, never()).deleteAnimal(animal.uuid());
            verify(repo, never()).deleteSnapshot(animal.uuid());
        }
    }

    @Test void heldGhostKeepsEverySqliteRowAndScanContinuesWithAccurateCounters() throws Exception {
        try (var farm = new HusbandryRuntimeCoverageTest.Farm(directory)) {
            var repo = farm.repository;
            HusbandryAnimal held = farm.ownedOffline("Held"), normal = farm.offline("COW", "Normal");
            farm.own(normal, farm.neighbour);
            var connection = repo.ownershipDatabase().getConnection();
            UUID death = UUID.randomUUID(), recipient = UUID.randomUUID();
            update(connection, "INSERT INTO owners VALUES (?, ?, 'owner')", held.uuid().toString(), farm.player.getUniqueId().toString());
            update(connection, "ALTER TABLE character_owners ADD COLUMN future BLOB");
            update(connection, "UPDATE character_owners SET future=? WHERE animal_uuid=?", new byte[]{0, 7, -1}, held.uuid().toString());
            repo.upsertSnapshots(Map.of(held.uuid(), new byte[]{0, 1, -1}, normal.uuid(), new byte[]{8, 9}), 73);
            update(connection, "INSERT INTO husbandry_estate_intents VALUES (?, 1, 'captured opaque decision', ?, ?, ?, ?, 'prepared')",
                    death.toString(), farm.player.getUniqueId().toString(), farm.player.getUniqueId().toString(), recipient.toString(), recipient.toString());
            update(connection, "INSERT INTO husbandry_estate_slots VALUES (?, 1, ?, 'owner', NULL, 'owner')", death.toString(), held.uuid().toString());
            var before = heldState(connection, held.uuid(), death);
            assertTrue(repo.isEstateHeld(held.uuid()));
            assertNull(HusbandryLocator.deleteGhost(repo, held));
            assertEquals(before, heldState(connection, held.uuid(), death));
            assertTrue(HusbandryLocator.isMissing(held.uuid()));
            assertEquals(1, repo.countForPlayer(farm.player.getUniqueId()));
            assertEquals(1, repo.countForPlayer(farm.neighbour.getUniqueId()));

            List<String> logs = new ArrayList<>();
            Handler handler = new Handler() {
                @Override public void publish(LogRecord record) { logs.add(record.getMessage()); }
                @Override public void flush() {}
                @Override public void close() {}
            };
            Logger logger = Bukkit.getLogger(); logger.addHandler(handler);
            try (MockedStatic<HusbandrySnapshots> snapshots = mockStatic(HusbandrySnapshots.class);
                 var scan = new HusbandryRuntimeCoverageTest.Scan(new HusbandryEntityScan.Result(Map.of(), Set.of(), true))) {
                snapshots.when(() -> HusbandrySnapshots.restore(any(), any(), anyList()))
                        .thenReturn(HusbandrySnapshots.RestoreResult.SKIPPED);
                HusbandryLocator.scanUnloaded(); scan.background(); assertDoesNotThrow(scan::apply);
            } finally { logger.removeHandler(handler); }
            assertEquals(before, heldState(connection, held.uuid(), death));
            assertArrayEquals(new byte[]{0, 1, -1}, repo.getSnapshot(held.uuid()).orElseThrow().data());
            assertTrue(repo.exists(held.uuid()));
            assertEquals(1, repo.countForPlayer(farm.player.getUniqueId()));
            assertEquals(0, repo.countForPlayer(farm.neighbour.getUniqueId()));
            assertFalse(repo.exists(normal.uuid()));
            assertTrue(repo.listOwners(normal.uuid()).isEmpty());
            assertTrue(repo.getSnapshot(normal.uuid()).isEmpty());
            assertTrue(logs.stream().anyMatch(line -> line.contains("dropped 1 ghost animals") && line.contains("held 1 missing animal records")));
            assertFalse(logs.stream().anyMatch(line -> line.startsWith("[BreedingBuddies] Dropped ghost animal") && line.contains(held.uuid().toString())));
        }
    }

    @Test void pendingReadinessPublishesOnNextMinuteThenReportsHeldPlansOnlyHourlyAndSurvivesRestart() throws Exception {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class); BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Logger logger = mock(Logger.class); when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler); when(plugin.getLogger()).thenReturn(logger);
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(20L * 60))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return null; });
        UUID account = UUID.randomUUID(), unknown = UUID.randomUUID(), character = UUID.randomUUID();
        var heir = new CharacterRef(account, character);
        AtomicBoolean ready = new AtomicBoolean(false);
        Path database = directory.resolve("retry.db");
        try (MockedStatic<Characters> characters = mockStatic(Characters.class)) {
            characters.when(Characters::legacyHeirsReady).thenAnswer(call -> ready.get());
            characters.when(() -> Characters.legacyHeir(any())).thenAnswer(call -> account.equals(call.getArgument(0)) ? Optional.of(heir) : Optional.empty());
            try (var fixture = new HusbandryOwnershipMigrationTest.Repository(database)) {
                var repo = spy(fixture.value); var connection = repo.ownershipDatabase().getConnection();
                var animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Late heir"); repo.upsertAnimal(animal);
                update(connection, "INSERT INTO owners VALUES (?, ?, 'owner')", animal.uuid().toString(), account.toString());
                update(connection, "INSERT INTO owners VALUES (?, ?, 'coowner')", animal.uuid().toString(), unknown.toString());
                HusbandryMigration.start(plugin, repo); Runnable task = tasks.getFirst();
                task.run(); task.run(); task.run();
                assertTrue(repo.listOwners(animal.uuid()).isEmpty()); assertEquals(1, repo.countForPlayer(account));
                characters.verify(() -> Characters.legacyHeir(any()), never());
                verify(logger, times(1)).warning(contains("waits for reviewed heirs"));
                ready.set(true); task.run();
                assertEquals(heir, repo.listOwners(animal.uuid()).getFirst().ref()); assertEquals(1, repo.heldOwners());
                assertEquals(1, repo.countForPlayer(unknown));
                var original = Arrays.deepToString(rows(connection, "SELECT * FROM husbandry_owner_archive ORDER BY player_uuid").toArray());
                clearInvocations(repo, logger); characters.clearInvocations();
                for (int minute = 0; minute < 59; minute++) task.run();
                verify(repo, never()).migrateOwners(any(), any()); verify(repo, never()).heldOwners();
                characters.verifyNoInteractions(); verifyNoInteractions(logger);
                task.run();
                verify(repo, times(1)).migrateOwners(any(), any()); verify(repo, times(1)).heldOwners();
                verify(logger, times(1)).warning(contains("holds 1 legacy slots"));
                assertEquals(original, Arrays.deepToString(rows(connection, "SELECT * FROM husbandry_owner_archive ORDER BY player_uuid").toArray()));
            }
            ready.set(false); characters.clearInvocations(); clearInvocations(logger);
            try (var fixture = new HusbandryOwnershipMigrationTest.Repository(database)) {
                var repo = spy(fixture.value); HusbandryMigration.start(plugin, repo); tasks.getLast().run();
                assertEquals(1, repo.heldOwners());
                characters.verifyNoInteractions();
                verify(logger, times(1)).warning(contains("holds 1 legacy slots"));
                assertEquals(2, scalar(repo.ownershipDatabase().getConnection(), "SELECT COUNT(*) FROM husbandry_owner_archive"));
                assertEquals(1, scalar(repo.ownershipDatabase().getConnection(), "SELECT COUNT(*) FROM character_owners"));
            }
        }
    }

    @Test void pendingFailuresRetryWithoutRepeatingIdenticalWarnings() {
        Plugin plugin = mock(Plugin.class); Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class); Logger logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler); when(plugin.getLogger()).thenReturn(logger);
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(20L * 60))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return null; });
        HusbandryRepository repo = mock(HusbandryRepository.class);
        when(repo.migrateOwners(any(), any())).thenThrow(new IllegalStateException("disk unavailable"), new IllegalStateException("disk unavailable")).thenReturn(true);
        HusbandryMigration.start(plugin, repo); Runnable task = tasks.getFirst();
        task.run(); task.run(); task.run();
        verify(repo, times(3)).migrateOwners(any(), any());
        verify(logger, times(1)).warning(contains("disk unavailable"));
        verify(repo).heldOwners();
    }

    private Map<String, String> heldState(Connection connection, UUID animal, UUID death) throws Exception {
        Map<String, String> state = new TreeMap<>();
        for (String table : List.of("animals", "owners", "character_owners", "snapshots", "husbandry_estate_intents", "husbandry_estate_slots")) {
            String key = table.equals("animals") ? "uuid" : table.equals("husbandry_estate_intents") ? "death_id" : "animal_uuid";
            var columns = rows(connection, "PRAGMA table_info(" + table + ")").stream().map(row -> "quote(\"" + row[1] + "\")").toList();
            state.put(table, Arrays.deepToString(rows(connection, "SELECT " + String.join(",", columns) + " FROM " + table + " WHERE " + key + "=? ORDER BY rowid",
                    (table.equals("husbandry_estate_intents") ? death : animal).toString()).toArray()));
        }
        return state;
    }
}
