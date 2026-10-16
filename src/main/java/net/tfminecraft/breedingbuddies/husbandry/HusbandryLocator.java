package net.tfminecraft.breedingbuddies.husbandry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

/**
 * Finds owned animals sitting in unloaded chunks by reading the saved entity chunks,
 * so {@code /animals} can point at them. An owned animal that is not in any saved chunk
 * of a fully scanned world, nor under a logged-out rider, was lost from the save: it is
 * respawned from its snapshot, or, without one, its row is deleted as a ghost and logged.
 */
public final class HusbandryLocator {

    private static final Set<UUID> MISSING = ConcurrentHashMap.newKeySet();

    private HusbandryLocator() {}

    public static boolean isMissing(UUID uuid) {
        return uuid != null && MISSING.contains(uuid);
    }

    static void markFound(UUID uuid) {
        if (uuid != null) {
            MISSING.remove(uuid);
        }
    }

    static void markMissing(UUID uuid) {
        if (uuid != null) {
            MISSING.add(uuid);
        }
    }

    /** Scans once in the background. Call on the main thread after loaded worlds have resumed. */
    public static void scanUnloaded() {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || BreedingBuddies.plugin == null) {
            return;
        }
        Map<UUID, Long> targets = new HashMap<>();
        for (HusbandryAnimal animal : repository.listOwnedAnimals()) {
            if (HusbandryEntities.getLoaded(animal.uuid()).isEmpty()) {
                targets.put(animal.uuid(), animal.unloadedAt());
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        List<HusbandryEntityScan.WorldDir> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(new HusbandryEntityScan.WorldDir(world.getName(), entitiesFolder(world)));
        }
        File playerdata = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "playerdata");
        Bukkit.getScheduler().runTaskAsynchronously(BreedingBuddies.plugin, () -> {
            HusbandryEntityScan.Result result = HusbandryEntityScan.scan(worlds, playerdata, targets.keySet());
            if (BreedingBuddies.plugin != null && BreedingBuddies.plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(BreedingBuddies.plugin, () -> apply(targets, result, worlds));
            }
        });
    }

    private static File entitiesFolder(World world) {
        File folder = world.getWorldFolder();
        return switch (world.getEnvironment()) {
            case NETHER -> new File(folder, "DIM-1/entities");
            case THE_END -> new File(folder, "DIM1/entities");
            default -> new File(folder, "entities");
        };
    }

    private static void apply(
            Map<UUID, Long> targets,
            HusbandryEntityScan.Result result,
            List<HusbandryEntityScan.WorldDir> worlds) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        int located = 0;
        int restoring = 0;
        int dropped = 0;
        int missing = 0;
        int held = 0;
        List<String> scannedWorlds = worlds.stream().map(HusbandryEntityScan.WorldDir::world).toList();
        for (Map.Entry<UUID, Long> target : targets.entrySet()) {
            UUID uuid = target.getKey();
            if (HusbandryEntities.getLoaded(uuid).isPresent()) {
                continue;
            }
            Optional<HusbandryAnimal> stored = repository.getAnimal(uuid);
            if (stored.isEmpty() || !Objects.equals(stored.get().unloadedAt(), target.getValue())) {
                // Loaded or unloaded again during the scan; the stored location is newer.
                continue;
            }
            if (result.ridden().contains(uuid)) {
                // Saved with a rider who logged out; it comes back when they join.
                MISSING.remove(uuid);
                continue;
            }
            HusbandryEntityScan.Found found = result.found().get(uuid);
            if (found == null) {
                HusbandryAnimal animal = stored.get();
                // An animal last seen in a world that was not scanned may still be there.
                if (!isConfirmedGhost(result.complete(), animal.world(), scannedWorlds)) {
                    continue;
                }
                try {
                    HusbandrySnapshots.RestoreResult restoration =
                            HusbandrySnapshots.restore(repository, animal, repository.listOwners(uuid));
                    if (restoration == HusbandrySnapshots.RestoreResult.SCHEDULED) {
                        MISSING.remove(uuid);
                        restoring++;
                        continue;
                    }
                    if (restoration == HusbandrySnapshots.RestoreResult.FAILED) {
                        MISSING.add(uuid);
                        missing++;
                        continue;
                    }
                } catch (SqliteDatabaseException ex) {
                    Bukkit.getLogger().severe("[BreedingBuddies] Failed to read the snapshot of " + uuid
                            + ": " + ex.getMessage());
                    MISSING.add(uuid);
                    missing++;
                    continue;
                }
                try {
                    String line = deleteGhost(repository, animal);
                    if (line == null) {
                        held++;
                        continue;
                    }
                    Bukkit.getLogger().warning(line);
                    dropped++;
                } catch (SqliteDatabaseException ex) {
                    Bukkit.getLogger().severe("[BreedingBuddies] Failed to drop ghost animal " + uuid
                            + ": " + ex.getMessage());
                    MISSING.add(uuid);
                    missing++;
                }
                continue;
            }
            MISSING.remove(uuid);
            if (sameLocation(stored.get(), found)) {
                continue;
            }
            HusbandryAnimal animal = stored.get();
            animal.setLastLocation(found.world(), found.x(), found.y(), found.z());
            try {
                repository.upsertAnimal(animal);
                located++;
            } catch (SqliteDatabaseException ex) {
                Bukkit.getLogger().severe("[BreedingBuddies] Failed to save husbandry animal location: " + ex.getMessage());
            }
        }
        if (located > 0 || restoring > 0 || dropped > 0 || missing > 0 || held > 0 || !result.complete()) {
            String failed = missing > 0
                    ? ", " + missing + " still marked missing after errors"
                    : "";
            Bukkit.getLogger().info("[BreedingBuddies] Animal scan: updated " + located + " locations, restoring "
                    + restoring + " lost animals from snapshots, dropped " + dropped + " ghost animals" + failed
                    + (held > 0 ? ", held " + held + " missing animal records for staff review" : "")
                    + (result.complete() ? "." : " (some chunks could not be read, none dropped)."));
        }
    }

    /**
     * A ghost is an owned animal absent from every saved chunk after a complete scan of its world.
     * An incomplete scan, or a last world that was not scanned, is not enough to drop the row.
     */
    static boolean isConfirmedGhost(boolean scanComplete, String storedWorld, Collection<String> scannedWorlds) {
        if (!scanComplete) {
            return false;
        }
        if (storedWorld == null || storedWorld.isBlank()) {
            return true;
        }
        return scannedWorlds != null && scannedWorlds.contains(storedWorld);
    }

    /** Deletes the animal and its owners, or returns null without mutation for an estate hold. */
    static String deleteGhost(HusbandryRepository repository, HusbandryAnimal animal) {
        if (repository.isEstateHeld(animal.uuid())) {
            MISSING.add(animal.uuid());
            return null;
        }
        List<HusbandryOwner> owners = repository.listOwners(animal.uuid());
        // Foreign-key cascades remove account owners, character owners and snapshots together.
        repository.deleteAnimal(animal.uuid());
        HusbandryEntities.evict(animal.uuid());
        MISSING.remove(animal.uuid());
        return ghostLog(animal, owners);
    }

    static String ghostLog(HusbandryAnimal animal, List<HusbandryOwner> owners) {
        String place = animal.hasLocation()
                ? animal.world() + " " + animal.x() + ", " + animal.y() + ", " + animal.z()
                : "unknown";
        return "[BreedingBuddies] Dropped ghost animal " + describe(animal, owners) + " last seen " + place;
    }

    /** Name, type, uuid and owners, as the ghost and restore log lines print them. */
    static String describe(HusbandryAnimal animal, List<HusbandryOwner> owners) {
        String name = animal.name() == null || animal.name().isBlank() ? "(unnamed)" : animal.name();
        String type = animal.type() == null || animal.type().isBlank() ? "unknown" : animal.type();
        String ownerText = owners == null || owners.isEmpty()
                ? "none"
                : owners.stream()
                        .sorted((left, right) -> left.playerUuid().compareTo(right.playerUuid()))
                        .map(owner -> owner.playerUuid() + " (" + owner.role() + ")")
                        .collect(Collectors.joining(", "));
        return name + " (" + type + ") " + animal.uuid() + " owners=" + ownerText;
    }

    private static boolean sameLocation(HusbandryAnimal animal, HusbandryEntityScan.Found found) {
        return animal.hasLocation()
                && found.world().equals(animal.world())
                && found.x() == animal.x()
                && found.y() == animal.y()
                && found.z() == animal.z();
    }
}
