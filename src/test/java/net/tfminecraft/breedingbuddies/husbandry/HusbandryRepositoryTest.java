package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HusbandryRepositoryTest {

    @Test
    void batchUpsertInsertsAndUpdates(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal first = animal(UUID.randomUUID(), "COW", "Bess", 12);
            HusbandryAnimal second = animal(UUID.randomUUID(), "SHEEP", "Wool", 4);
            HusbandryAnimal third = animal(UUID.randomUUID(), "PIG", "Ham", 8);
            repository.upsertAnimals(List.of(first, second, third));

            assertEquals(12, repository.getAnimal(first.uuid()).orElseThrow().care());
            assertEquals("Wool", repository.getAnimal(second.uuid()).orElseThrow().name());
            assertEquals("PIG", repository.getAnimal(third.uuid()).orElseThrow().type());

            first.setCare(40);
            first.setGenetics(250);
            first.setHungrySince(1_700_000_000_000L);
            first.setCareUpRemainderSeconds(123);
            first.setCareDownRemainderSeconds(45);
            repository.upsertAnimals(Arrays.asList(first, null));

            HusbandryAnimal reloaded = repository.getAnimal(first.uuid()).orElseThrow();
            assertEquals(40, reloaded.care());
            assertEquals(250, reloaded.genetics());
            assertEquals(1_700_000_000_000L, reloaded.hungrySince());
            assertEquals(123, reloaded.careUpRemainderSeconds());
            assertEquals(45, reloaded.careDownRemainderSeconds());
            assertEquals(4, repository.getAnimal(second.uuid()).orElseThrow().care());
            assertTrue(repository.exists(third.uuid()));
        } finally {
            repository.close();
        }
    }

    @Test
    void missingRevisionResetsToWildAndStamps(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "SHEEP", "Bess", 40);
            animal.setGenetics(900);
            animal.setNeutered(true);
            animal.setMatureAt(12_345L);
            animal.setCareUpRemainderSeconds(9);
            animal.setCareDownRemainderSeconds(3);
            repository.upsertAnimal(animal);

            Random random = new Random(1L);
            int expectedGenes = new Random(1L).nextInt(HusbandryConfig.initialGeneticMax() + 1);
            int reset = repository.resetStaleStats("1", random);
            assertEquals(1, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(expectedGenes, reloaded.genetics());
            assertEquals(0, reloaded.care());
            assertEquals(0, reloaded.careUpRemainderSeconds());
            assertEquals(0, reloaded.careDownRemainderSeconds());
            assertEquals("1", reloaded.statsRevision());
            assertEquals("Bess", reloaded.name());
            assertTrue(reloaded.neutered());
            assertEquals(12_345L, reloaded.matureAt());
        } finally {
            repository.close();
        }
    }

    @Test
    void matchingRevisionIsUnchanged(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "COW", "Bess", 40);
            animal.setGenetics(250);
            animal.setStatsRevision("1");
            repository.upsertAnimal(animal);

            int reset = repository.resetStaleStats("1", new Random(1L));
            assertEquals(0, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(250, reloaded.genetics());
            assertEquals(40, reloaded.care());
            assertEquals("1", reloaded.statsRevision());
        } finally {
            repository.close();
        }
    }

    @Test
    void bumpedRevisionResetsMatchingRows(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal animal = animal(UUID.randomUUID(), "PIG", "Ham", 80);
            animal.setGenetics(700);
            animal.setStatsRevision("1");
            repository.upsertAnimal(animal);

            Random random = new Random(2L);
            int expectedGenes = new Random(2L).nextInt(HusbandryConfig.initialGeneticMax() + 1);
            int reset = repository.resetStaleStats("2", random);
            assertEquals(1, reset);

            HusbandryAnimal reloaded = repository.getAnimal(animal.uuid()).orElseThrow();
            assertEquals(expectedGenes, reloaded.genetics());
            assertEquals(0, reloaded.care());
            assertEquals("2", reloaded.statsRevision());
            assertEquals("Ham", reloaded.name());
        } finally {
            repository.close();
        }
    }

    @Test
    void locationRoundTripsAndListsOnlyThatPlayersAnimals(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            UUID player = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            HusbandryAnimal cow = animal(UUID.randomUUID(), "COW", "Bess", 10);
            cow.setLastLocation("world", 120, 64, -340);
            HusbandryAnimal sheep = animal(UUID.randomUUID(), "SHEEP", "Woolly", 4);
            HusbandryAnimal pig = animal(UUID.randomUUID(), "PIG", "Ham", 1);
            repository.upsertAnimals(List.of(cow, sheep, pig));
            repository.upsertOwner(new HusbandryOwner(cow.uuid(), player, player, "owner"));
            repository.upsertOwner(new HusbandryOwner(sheep.uuid(), player, player, "coowner"));
            repository.upsertOwner(new HusbandryOwner(sheep.uuid(), other, other, "owner"));
            repository.upsertOwner(new HusbandryOwner(pig.uuid(), other, other, "owner"));

            HusbandryAnimal reloaded = repository.getAnimal(cow.uuid()).orElseThrow();
            assertEquals("world", reloaded.world());
            assertEquals(120, reloaded.x());
            assertEquals(64, reloaded.y());
            assertEquals(-340, reloaded.z());
            assertFalse(repository.getAnimal(sheep.uuid()).orElseThrow().hasLocation());

            List<HusbandryOwned> mine = repository.listForPlayer(player);
            assertEquals(2, mine.size());
            assertEquals("owner", roleOf(mine, cow.uuid()));
            assertEquals("coowner", roleOf(mine, sheep.uuid()));
            assertEquals(1, repository.listForPlayer(other).stream()
                    .filter(row -> row.animal().uuid().equals(pig.uuid()))
                    .count());
            assertTrue(repository.listForPlayer(UUID.randomUUID()).isEmpty());

            HusbandryAnimal wild = animal(UUID.randomUUID(), "COW", "", 0);
            repository.upsertAnimal(wild);
            assertEquals(
                    java.util.Set.of(cow.uuid(), sheep.uuid(), pig.uuid()),
                    repository.listOwnedAnimals().stream()
                            .map(HusbandryAnimal::uuid)
                            .collect(java.util.stream.Collectors.toSet()));
        } finally {
            repository.close();
        }
    }

    @Test
    void versionSevenGainsLocationColumnsWithoutLosingRows(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("legacy.db").toFile();
        UUID uuid = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            connection.createStatement().execute("""
                    CREATE TABLE animals (
                        uuid TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        state TEXT NOT NULL DEFAULT 'untamed',
                        genetics INTEGER NOT NULL DEFAULT 0,
                        care INTEGER NOT NULL DEFAULT 0,
                        hungry_since INTEGER,
                        dirty_since INTEGER,
                        last_processed_at INTEGER NOT NULL,
                        unloaded_at INTEGER,
                        affliction_elapsed REAL NOT NULL DEFAULT 0,
                        affliction_at REAL NOT NULL DEFAULT 0,
                        last_milk_at INTEGER,
                        wool_ready_at INTEGER,
                        neutered INTEGER NOT NULL DEFAULT 0,
                        loaded_visit_start INTEGER,
                        mature_at INTEGER,
                        shed_ready_at INTEGER,
                        egg_ready_at INTEGER,
                        care_up_remainder INTEGER NOT NULL DEFAULT 0,
                        care_down_remainder INTEGER NOT NULL DEFAULT 0,
                        stats_revision TEXT
                    )
                    """);
            connection.createStatement().execute("PRAGMA user_version = 7");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO animals (uuid, type, name, state, genetics, care, last_processed_at, stats_revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, "GOAT");
                insert.setString(3, "Nanny");
                insert.setString(4, "owned");
                insert.setInt(5, 440);
                insert.setInt(6, 80);
                insert.setLong(7, 50L);
                insert.setString(8, "1");
                insert.executeUpdate();
            }
        }

        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal loaded = repository.getAnimal(uuid).orElseThrow();
            assertEquals("Nanny", loaded.name());
            assertEquals(440, loaded.genetics());
            assertEquals(80, loaded.care());
            assertFalse(loaded.hasLocation());
            assertNull(loaded.world());
            loaded.setLastLocation("tfmc_world", 3, 70, 9);
            repository.upsertAnimal(loaded);
        } finally {
            repository.close();
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            assertEquals(8, connection.createStatement().executeQuery("PRAGMA user_version").getInt(1));
        }
        HusbandryRepository again = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal loaded = again.getAnimal(uuid).orElseThrow();
            assertEquals("tfmc_world", loaded.world());
            assertEquals(3, loaded.x());
            assertEquals(70, loaded.y());
            assertEquals(9, loaded.z());
            assertEquals(440, loaded.genetics());
        } finally {
            again.close();
        }
    }

    private static String roleOf(List<HusbandryOwned> rows, UUID animalUuid) {
        return rows.stream()
                .filter(row -> animalUuid.equals(row.animal().uuid()))
                .findFirst()
                .orElseThrow()
                .role();
    }

    @Test
    void snapshotsRoundTripAndGoWithTheirAnimal(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("husbandry.db").toFile();
        HusbandryRepository repository = HusbandryRepository.open(dbFile);
        try {
            HusbandryAnimal kept = animal(UUID.randomUUID(), "HORSE", "Drake Maye", 200);
            HusbandryAnimal dropped = animal(UUID.randomUUID(), "HORSE", "gregor", 200);
            UUID noRow = UUID.randomUUID();
            repository.upsertAnimals(List.of(kept, dropped));

            repository.upsertSnapshots(
                    java.util.Map.of(kept.uuid(), new byte[] {1, 2, 3}, dropped.uuid(), new byte[] {4}, noRow,
                            new byte[] {5}),
                    100L);
            HusbandrySnapshot stored = repository.getSnapshot(kept.uuid()).orElseThrow();
            assertTrue(Arrays.equals(new byte[] {1, 2, 3}, stored.data()));
            assertEquals(100L, stored.savedAt());
            assertTrue(repository.getSnapshot(noRow).isEmpty());

            repository.upsertSnapshots(java.util.Map.of(kept.uuid(), new byte[] {9}), 200L);
            stored = repository.getSnapshot(kept.uuid()).orElseThrow();
            assertTrue(Arrays.equals(new byte[] {9}, stored.data()));
            assertEquals(200L, stored.savedAt());

            repository.deleteAnimal(dropped.uuid());
            assertTrue(repository.getSnapshot(dropped.uuid()).isEmpty());

            repository.deleteSnapshot(kept.uuid());
            assertTrue(repository.getSnapshot(kept.uuid()).isEmpty());
            assertTrue(repository.exists(kept.uuid()));
            assertTrue(repository.getSnapshot(null).isEmpty());
        } finally {
            repository.close();
        }
    }

    private static HusbandryAnimal animal(UUID uuid, String type, String name, int care) {
        HusbandryAnimal animal = new HusbandryAnimal(uuid, type, name);
        animal.setCare(care);
        animal.setGenetics(100);
        animal.setLastProcessedAt(1_234L);
        animal.setLoadedVisitStart(2_000L);
        return animal;
    }
}
