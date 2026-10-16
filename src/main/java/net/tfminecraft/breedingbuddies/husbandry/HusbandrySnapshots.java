package net.tfminecraft.breedingbuddies.husbandry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import io.papermc.paper.entity.EntitySerializationFlag;
import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

/**
 * Keeps a serialized copy of every loaded owned animal so one that a crash or forced stop
 * drops from the world save can be spawned again, instead of being deleted as a ghost.
 */
public final class HusbandrySnapshots {

    enum RestoreResult { SKIPPED, SCHEDULED, FAILED }

    private HusbandrySnapshots() {}

    public static boolean shouldCapture(HusbandryAnimal animal) {
        return animal != null && animal.state() == HusbandryAnimalState.OWNED;
    }

    /** Returns the entity bytes, or null when Paper refuses to serialize it. */
    @SuppressWarnings("deprecation")
    public static byte[] capture(Entity entity, boolean force) {
        if (entity == null) {
            return null;
        }
        try {
            return force
                    ? Bukkit.getUnsafe().serializeEntity(entity, EntitySerializationFlag.FORCE)
                    : Bukkit.getUnsafe().serializeEntity(entity);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static void save(HusbandryRepository repository, Map<UUID, byte[]> snapshots) {
        if (repository == null || snapshots.isEmpty()) {
            return;
        }
        try {
            repository.upsertSnapshots(snapshots, System.currentTimeMillis());
        } catch (SqliteDatabaseException ex) {
            Bukkit.getLogger().severe("[BreedingBuddies] Failed to save husbandry snapshots: " + ex.getMessage());
        }
    }

    /**
     * Chunk unloads and riders logging out leave the animal in a save file, so its snapshot stays.
     * Any other removal is deliberate, and restoring the animal later would bring it back.
     */
    public static boolean keepsSnapshot(EntityRemoveEvent.Cause cause) {
        return cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT;
    }

    /**
     * Spawns a lost animal from its snapshot at the place it was captured.
     * SKIPPED means restoration is disabled or there is no snapshot. FAILED preserves the records for
     * retry or staff review; SCHEDULED means the snapshot is waiting for its chunk to load.
     */
    @SuppressWarnings("deprecation")
    static RestoreResult restore(HusbandryRepository repository, HusbandryAnimal animal, List<HusbandryOwner> owners) {
        if (repository == null || animal == null || BreedingBuddies.plugin == null) {
            return RestoreResult.FAILED;
        }
        if (!HusbandryConfig.restoreLostAnimals()) {
            return RestoreResult.SKIPPED;
        }
        Optional<HusbandrySnapshot> snapshot = repository.getSnapshot(animal.uuid());
        if (snapshot.isEmpty()) {
            return RestoreResult.SKIPPED;
        }
        World world = animal.world() == null ? null : Bukkit.getWorld(animal.world());
        if (world == null) {
            return RestoreResult.FAILED;
        }
        Entity entity;
        try {
            entity = Bukkit.getUnsafe().deserializeEntity(snapshot.get().data(), world, true);
        } catch (RuntimeException ex) {
            Bukkit.getLogger().severe("[BreedingBuddies] Could not read the snapshot of " + animal.uuid()
                    + ": " + ex.getMessage());
            return RestoreResult.FAILED;
        }
        if (entity == null || !entity.getUniqueId().equals(animal.uuid())) {
            Bukkit.getLogger().severe("[BreedingBuddies] Snapshot of " + animal.uuid()
                    + " does not contain the expected entity; records kept for staff review.");
            return RestoreResult.FAILED;
        }
        Location at = entity.getLocation();
        at.setWorld(world);
        world.getChunkAtAsync(at.getBlockX() >> 4, at.getBlockZ() >> 4).whenComplete((chunk, error) -> {
            if (BreedingBuddies.plugin == null || !BreedingBuddies.plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(BreedingBuddies.plugin, () -> {
                if (error != null) {
                    Bukkit.getLogger().severe("[BreedingBuddies] Could not load the chunk to restore " + animal.uuid()
                            + ": " + error.getMessage());
                    HusbandryLocator.markMissing(animal.uuid());
                    return;
                }
                finish(animal, owners, entity, at, snapshot.get().savedAt());
            });
        });
        return RestoreResult.SCHEDULED;
    }

    static void finish(HusbandryAnimal animal, List<HusbandryOwner> owners, Entity entity, Location at, long savedAt) {
        if (Bukkit.getEntity(animal.uuid()) != null) {
            // It came back with its chunk after all, so spawning the copy would duplicate it.
            return;
        }
        if (!entity.spawnAt(at, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            Bukkit.getLogger().warning("[BreedingBuddies] Could not respawn lost animal " + animal.uuid()
                    + "; it stays missing and is retried at the next start.");
            HusbandryLocator.markMissing(animal.uuid());
            return;
        }
        HusbandryLifecycleListener.handleLoad(entity);
        Bukkit.getLogger().warning(restoreLog(animal, owners, at, savedAt));
    }

    static String restoreLog(HusbandryAnimal animal, List<HusbandryOwner> owners, Location at, long savedAt) {
        return "[BreedingBuddies] Restored lost animal " + HusbandryLocator.describe(animal, owners)
                + " at " + at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY() + ", "
                + at.getBlockZ() + " from its snapshot of " + Instant.ofEpochMilli(savedAt);
    }
}
