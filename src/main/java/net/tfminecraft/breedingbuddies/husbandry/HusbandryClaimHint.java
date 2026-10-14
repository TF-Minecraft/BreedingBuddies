package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/** Tells players who tame or ride an unowned animal how to claim it, and their animal limit. */
public final class HusbandryClaimHint {

    static final long COOLDOWN_MILLIS = 60_000L;

    private static final Map<UUID, Long> LAST_SENT = new ConcurrentHashMap<>();

    private HusbandryClaimHint() {}

    /** True when the chunk-load cleanup would remove this animal, using the same rule. */
    public static boolean needsClaim(Entity entity) {
        if (entity == null || !HusbandryConfig.isRemoveUnowned(entity.getType())) {
            return false;
        }
        UUID uuid = entity.getUniqueId();
        HusbandryRepository repository = HusbandryEntities.repository();
        boolean hasRow = HusbandryEntities.getLoaded(uuid).isPresent()
                || (repository != null && repository.exists(uuid));
        return HusbandryMounts.shouldWipeUnowned(
                true,
                HusbandryOwnershipService.hasAnyOwner(uuid),
                hasRow,
                HusbandryMounts.hasConfiguredStats(entity));
    }

    /** Any listed animal without an owner, including ridden mounts the cleanup keeps as wild. */
    public static boolean isUnclaimed(Entity entity) {
        return entity != null
                && HusbandryConfig.isRemoveUnowned(entity.getType())
                && !HusbandryOwnershipService.hasAnyOwner(entity.getUniqueId());
    }

    /** Sends the hint unless this player saw one within the cooldown. */
    public static void remind(Player player, Entity entity) {
        long now = System.currentTimeMillis();
        Long last = LAST_SENT.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return;
        }
        send(player, entity, entity instanceof Tameable tameable && !tameable.isTamed());
    }

    /**
     * {@code untamed} comes from the caller: during {@code EntityTameEvent} the animal is not tamed yet,
     * so reading it from the entity would tell a player who just tamed it to tame it first.
     */
    public static void send(Player player, Entity entity, boolean untamed) {
        if (player == null || !isUnclaimed(entity)) {
            return;
        }
        // Riding enrolls a mount, and the cleanup keeps enrolled mounts as unowned wild animals.
        boolean staysWild = !needsClaim(entity);
        LAST_SENT.put(player.getUniqueId(), System.currentTimeMillis());
        boolean claimable = HusbandryConfig.species(entity.getType()) != null;
        boolean staff = HusbandryOwnershipService.isStaff(player);
        HusbandryRepository repository = HusbandryEntities.repository();
        int owned = repository == null ? 0 : repository.countForPlayer(player.getUniqueId());
        for (String line : lines(HusbandryEntities.displayName(entity.getType()),
                claimable, untamed, staysWild, owned, HusbandryConfig.maxAnimals(), staff)) {
            player.sendMessage(line);
        }
    }

    public static void forget(UUID playerUuid) {
        LAST_SENT.remove(playerUuid);
    }

    static List<String> lines(
            String species,
            boolean claimable,
            boolean untamed,
            boolean staysWild,
            int owned,
            int cap,
            boolean staff) {
        String label = species == null || species.isBlank() ? "animal" : species.toLowerCase(Locale.ROOT);
        String warning = staysWild
                ? "§eThis " + label + " is not claimed. Anyone can ride it or claim it until someone does."
                : "§eThis " + label + " is not claimed. Unclaimed animals wander off before"
                        + " anyone next comes by.";
        if (!claimable) {
            return List.of(warning, "§7This kind of animal cannot be claimed, so it will not stay.");
        }
        String claim = (untamed ? "Tame it, then right-click" : "Right-click")
                + " it with an Ownership Token renamed on an anvil";
        if (staff) {
            return List.of(warning,
                    "§7" + claim + ". §8(staff: no animal limit)");
        }
        if (owned >= cap) {
            return List.of(warning,
                    "§cYou already own " + owned + "/" + cap + " animals, so you cannot claim it.",
                    "§7Release one first: shift-right-click it with an empty hand, then click Remove ownership."
                            + " See §f/animals§7.");
        }
        return List.of(warning,
                "§7" + claim + " to keep it.",
                "§7You own §f" + owned + "/" + cap + "§7 animals. See §f/animals§7.");
    }
}
