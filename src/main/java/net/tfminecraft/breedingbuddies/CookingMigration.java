package net.tfminecraft.breedingbuddies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Takes over the animal records Cooking kept before BreedingBuddies ran livestock. Runs before the database
 * opens; Cooking's files are copied, never moved, so they stay as a backup. Settings are not copied:
 * config.yml starts from BreedingBuddies' own defaults, which changed how trust, mounts and products work.
 */
final class CookingMigration {
    static final String DATABASE = "Data/husbandry.db";

    private CookingMigration() {}

    static void run(File dataFolder, Logger logger) throws IOException {
        File cooking = new File(dataFolder.getParentFile(), "Cooking");
        copyDatabase(new File(cooking, DATABASE), new File(dataFolder, DATABASE), logger);
    }

    /** Publish the database atomically after its journal; failed copies remain safe to retry. */
    private static void copyDatabase(File source, File target, Logger logger) throws IOException {
        if (target.exists() || !source.isFile()) {
            return;
        }
        Path staged = Path.of(target.getPath() + ".migrating");
        try {
            Files.copy(source.toPath(), staged, StandardCopyOption.REPLACE_EXISTING);
            // A previous attempt may have left a journal that no longer belongs to Cooking's current database.
            Files.deleteIfExists(Path.of(target.getPath() + "-wal"));
            Files.deleteIfExists(Path.of(target.getPath() + "-shm"));
            File wal = new File(source.getPath() + "-wal");
            if (wal.isFile()) {
                Files.copy(wal.toPath(), Path.of(target.getPath() + "-wal"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(staged, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staged);
        }
        logger.info("Took over Cooking's animal records from " + source.getPath() + "; Cooking's copy is kept as a backup.");
    }
}
