package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.tlibs.utils.TimeFormatter;

public final class HusbandryInspectGui {

    private static final long MILLIS_PER_HOUR = 3_600_000L;
    private static final int INVENTORY_SIZE = 54;
    private static final int PRODUCTS_SLOT = 3;
    static final int REMOVE_SLOT = 8;
    private static final int CARE_BAR_START = 20;
    private static final int GENETICS_BAR_START = 38;
    private static final int MOUNT_HEALTH_SLOT = 46;
    private static final int MOUNT_SPEED_SLOT = 49;
    private static final int MOUNT_JUMP_SLOT = 52;

    private HusbandryInspectGui() {}

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void open(Player player, LivingEntity entity, HusbandryAnimal animal) {
        if (player == null || entity == null || animal == null) {
            return;
        }
        boolean mount = HusbandryMounts.isMount(entity);
        HusbandryInspectHolder holder = new HusbandryInspectHolder(animal.uuid(), HusbandryIdentity.session(player).orElse(null));
        String displayName = animal.name();
        if (displayName == null || displayName.isBlank() || "???".equals(displayName.trim())) {
            displayName = HusbandryEntities.displayName(entity.getType());
        }
        String title = "§6" + displayName;
        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.setInventory(inv);

        int careMax = HusbandryConfig.careMax();
        int yieldPct = HusbandryGuiBars.yieldPercent(animal);

        inv.setItem(0, statusItem(animal));
        inv.setItem(1, named(Material.SHEARS, animal.neutered() ? "§cNeutered" : "§aNot neutered", List.of()));
        inv.setItem(2, ownersItem(animal.uuid()));
        inv.setItem(4, named(Material.NAME_TAG, "§6" + displayName,
                List.of("§7" + entity.getType().name(), "§7State: " + animal.state().storage(),
                        growingLine(animal))));
        ItemStack products = productsItem(entity.getType(), yieldPct);
        if (products != null) {
            inv.setItem(PRODUCTS_SLOT, products);
        }

        HusbandryGuiBars.fillBar(inv, CARE_BAR_START, "Care", animal.care(), careMax);
        HusbandryGuiBars.fillBar(inv, GENETICS_BAR_START, "Genetics", animal.genetics(),
                HusbandryConfig.maxGenetics());

        if (mount) {
            inv.setItem(MOUNT_HEALTH_SLOT, named(Material.GOLDEN_APPLE,
                    "§dHealth: " + String.format("%.1f", HusbandryMounts.healthHearts(entity)) + " hearts",
                    List.of()));
            inv.setItem(MOUNT_SPEED_SLOT, named(Material.FEATHER,
                    "§bSpeed: " + String.format("%.2f", HusbandryMounts.speedBlocksPerSecond(entity)) + " b/s",
                    List.of()));
            inv.setItem(MOUNT_JUMP_SLOT, named(Material.LEATHER_BOOTS,
                    "§aJump: " + String.format("%.2f", HusbandryMounts.jumpBlockHeight(entity)) + " blocks",
                    List.of()));
        }

        if (HusbandryOwnershipService.isOwner(player, animal.uuid())) {
            inv.setItem(REMOVE_SLOT, named(Material.WRITABLE_BOOK, "§4Remove ownership",
                    List.of("§7Click to stop owning this animal.")));
        }
        player.openInventory(inv);
    }

    private static ItemStack productsItem(EntityType type, int yieldPct) {
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species == null || !HusbandryProducts.hasProducts(species)) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        lore.add("§aYield " + yieldPct + "%");
        for (String line : HusbandryProducts.modeLines(species)) {
            lore.add("§7" + line);
        }
        return named(Material.CHEST, "§6Products", lore);
    }

    private static String growingLine(HusbandryAnimal animal) {
        if (HusbandryGrowth.isMature(animal, System.currentTimeMillis())) {
            return "§7Mature";
        }
        Long matureAt = animal.matureAt();
        long remaining = Math.max(0L, matureAt - System.currentTimeMillis());
        return "§7Grows up in " + TimeFormatter.formatTime((int) (remaining / 1000L));
    }

    private static ItemStack statusItem(HusbandryAnimal animal) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (!hungry && !dirty) {
            return named(Material.LIME_DYE, "§aHappy", List.of("§7No Hungry or Dirty."));
        }
        List<String> lore = new ArrayList<>();
        if (hungry) {
            lore.add("§6Hungry");
        }
        if (dirty) {
            lore.add("§eDirty");
        }
        lore.add(decayLine(animal));
        return named(Material.ROTTEN_FLESH, "§cNeeds care", lore);
    }

    private static String decayLine(HusbandryAnimal animal) {
        Long hungry = animal.hungrySince();
        Long dirty = animal.dirtySince();
        long earliest = hungry == null ? dirty : (dirty == null ? hungry : Math.min(hungry, dirty));
        long decayStart = earliest + HusbandryConfig.decayGraceSeconds() * 1000L;
        long now = System.currentTimeMillis();
        if (now < decayStart) {
            double hours = (decayStart - now) / (double) MILLIS_PER_HOUR;
            return "§eDecay in " + formatHours(hours) + "h";
        }
        return "§cDecaying (−" + trim(HusbandryConfig.careDownAmount()) + " / "
                + TimeFormatter.formatTime(HusbandryConfig.careDownIntervalSeconds()) + ")";
    }

    private static ItemStack ownersItem(UUID animalUuid) {
        List<String> lore = new ArrayList<>();
        for (HusbandryOwner owner : HusbandryOwnershipService.listOwners(animalUuid)) {
            String name = HusbandryIdentity.name(owner.ref());
            lore.add("§7" + owner.role() + ": " + name);
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository != null && repository.hasHeldOwner(animalUuid)) lore.add("§7Unresolved owner slot (staff review)");
        if (lore.isEmpty()) {
            lore.add("§7None (untamed)");
        }
        return named(Material.PLAYER_HEAD, "§eOwners", lore);
    }

    private static String formatHours(double hours) {
        return String.format("%.1f", Math.max(0, hours));
    }

    private static String trim(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
