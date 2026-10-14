package net.tfminecraft.breedingbuddies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Takes over the animal records and settings Cooking kept before BreedingBuddies ran livestock.
 * Runs before the database opens; Cooking's files are copied, never moved, so they stay as a backup.
 */
final class CookingMigration {
    static final String DATABASE = "Data/husbandry.db";

    private CookingMigration() {}

    static void run(File dataFolder, Logger logger) throws IOException {
        File cooking = new File(dataFolder.getParentFile(), "Cooking");
        copyDatabase(new File(cooking, DATABASE), new File(dataFolder, DATABASE), logger);
        copyConfig(new File(cooking, "husbandry.yml"), new File(dataFolder, "config.yml"), logger);
    }

    /** The database file is copied last, so it only exists once the takeover is complete. */
    private static void copyDatabase(File source, File target, Logger logger) throws IOException {
        if (target.exists() || !source.isFile()) {
            return;
        }
        File wal = new File(source.getPath() + "-wal");
        if (wal.isFile()) {
            Files.copy(wal.toPath(), new File(target.getPath() + "-wal").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Took over Cooking's animal records from " + source.getPath() + "; Cooking's copy is kept as a backup.");
    }

    private static void copyConfig(File source, File target, Logger logger) throws IOException {
        if (target.exists() || !source.isFile()) {
            return;
        }
        Files.copy(source.toPath(), target.toPath());
        logger.info("Copied Cooking's husbandry.yml to config.yml; its enabled key is ignored here.");
    }
}
