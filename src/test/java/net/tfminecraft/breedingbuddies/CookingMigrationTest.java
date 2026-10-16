package net.tfminecraft.breedingbuddies;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CookingMigrationTest {
    @TempDir Path plugins;
    private final Logger logger = mock(Logger.class);
    private File data;
    private Path cooking;

    @BeforeEach void setUp() throws Exception {
        data = plugins.resolve("BreedingBuddies").toFile();
        Files.createDirectories(data.toPath().resolve("Data"));
        cooking = plugins.resolve("Cooking");
    }

    @Test void aFreshServerWithoutCookingRecordsHasNothingToTakeOver() throws Exception {
        CookingMigration.run(data, logger);

        assertFalse(Files.exists(data.toPath().resolve(CookingMigration.DATABASE)));
        assertFalse(Files.exists(data.toPath().resolve("config.yml")));
        verifyNoInteractions(logger);
    }

    @Test void theFirstStartCopiesCookingsDatabaseAndJournalButNotItsSettings() throws Exception {
        write(cooking.resolve("Data/husbandry.db"), "records");
        write(cooking.resolve("Data/husbandry.db-wal"), "journal");
        write(cooking.resolve("Data/husbandry.db-shm"), "index");
        write(cooking.resolve("husbandry.yml"), "enabled: false\nmax-animals: 9\n");

        CookingMigration.run(data, logger);

        Path target = data.toPath().resolve(CookingMigration.DATABASE);
        assertEquals("records", Files.readString(target));
        assertEquals("journal", Files.readString(Path.of(target + "-wal")));
        assertFalse(Files.exists(Path.of(target + "-shm")), "SQLite rebuilds the index; a stale one is never copied");
        assertFalse(Files.exists(data.toPath().resolve("config.yml")), "BreedingBuddies starts from its own defaults");
        assertTrue(Files.exists(cooking.resolve("Data/husbandry.db")));
        verify(logger).info(anyString());
    }

    @Test void anUnfinishedCopyIsRedoneButExistingRecordsAreNeverReplaced() throws Exception {
        write(cooking.resolve("Data/husbandry.db"), "records");
        write(data.toPath().resolve(CookingMigration.DATABASE + "-wal"), "partial");
        write(data.toPath().resolve(CookingMigration.DATABASE + "-shm"), "stale index");
        write(data.toPath().resolve(CookingMigration.DATABASE + ".migrating"), "partial database");

        CookingMigration.run(data, logger);

        Path target = data.toPath().resolve(CookingMigration.DATABASE);
        assertEquals("records", Files.readString(target));
        assertFalse(Files.exists(Path.of(target + "-wal")), "A journal from an interrupted copy must not replay");
        assertFalse(Files.exists(Path.of(target + "-shm")));
        assertFalse(Files.exists(Path.of(target + ".migrating")));

        write(cooking.resolve("Data/husbandry.db"), "newer");
        CookingMigration.run(data, logger);

        assertEquals("records", Files.readString(target));
    }

    @Test void retryDoesNotReplayAnOldJournalOverCookingsNewerRecords() throws Exception {
        Path source = cooking.resolve(CookingMigration.DATABASE);
        Path target = data.toPath().resolve(CookingMigration.DATABASE);
        Files.createDirectories(source.getParent());
        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            execute(database, "PRAGMA journal_mode=WAL");
            execute(database, "CREATE TABLE animals (name TEXT)");
            execute(database, "INSERT INTO animals VALUES ('Old horse')");
            // The first attempt stopped after copying the journal, before publishing the database.
            Files.copy(Path.of(source + "-wal"), Path.of(target + "-wal"));
        }
        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            execute(database, "UPDATE animals SET name='New horse'");
        }
        assertFalse(Files.exists(Path.of(source + "-wal")), "Cooking checkpointed its newer records");

        CookingMigration.run(data, logger);

        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + target);
                var statement = database.createStatement();
                var rows = statement.executeQuery("SELECT name FROM animals")) {
            assertTrue(rows.next());
            assertEquals("New horse", rows.getString(1));
        }
    }

    @Test void aFailedDatabaseCopyCannotLookLikeACompletedMigration() throws Exception {
        Path source = cooking.resolve(CookingMigration.DATABASE);
        Path target = data.toPath().resolve(CookingMigration.DATABASE);
        Path staged = Path.of(target + ".migrating");
        write(source, "complete records");
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING)).thenAnswer(call -> {
                Files.writeString(staged, "partial");
                throw new IOException("Interrupted database copy");
            });
            assertThrows(IOException.class, () -> CookingMigration.run(data, logger));
        }
        assertFalse(Files.exists(target), "Only a complete database may mark the migration as done");
        assertFalse(Files.exists(staged));

        CookingMigration.run(data, logger);

        assertEquals("complete records", Files.readString(target));
    }

    private static void execute(Connection database, String sql) throws Exception {
        try (var statement = database.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
