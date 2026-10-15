package net.tfminecraft.breedingbuddies;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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

        CookingMigration.run(data, logger);

        Path target = data.toPath().resolve(CookingMigration.DATABASE);
        assertEquals("records", Files.readString(target));
        assertEquals("partial", Files.readString(Path.of(target + "-wal")), "Without a Cooking journal nothing overwrites it");

        write(cooking.resolve("Data/husbandry.db"), "newer");
        CookingMigration.run(data, logger);

        assertEquals("records", Files.readString(target));
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
