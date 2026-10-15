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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.tlibs.utils.TimeFormatter;

/**
 * The animal menu, laid out like the original BreedingBuddies one: a header row, a row of trust hearts,
 * a row of genetics and, for mounts, a row of stats. The last slot lets an owner give the animal up.
 */
public final class HusbandryInspectGui {

    private static final long MILLIS_PER_HOUR = 3_600_000L;
    static final int OWNERS_SLOT = 0;
    static final int NAME_SLOT = 3;
    static final int PRODUCTS_SLOT = 4;
    static final int STATUS_SLOT = 5;
    static final int NEUTERED_SLOT = 8;
    static final int TRUST_START = 11;
    static final int GENETICS_START = 20;
    static final int HEALTH_SLOT = 30;
    static final int SPEED_SLOT = 31;
    static final int JUMP_SLOT = 32;
    static final int SEGMENTS = 5;
    private static final TextColor GOLD = TextColor.color(0xF2C14E);
    private static final TextColor TRUST = TextColor.color(0xFF5577);
    private static final TextColor GENES = TextColor.color(0x7ED957);

    private HusbandryInspectGui() {}

    /** The owner's give-up button sits in the last slot of the menu. */
    static int removeSlot(Inventory inventory) {
        return inventory.getSize() - 1;
    }

    public static void open(Player player, LivingEntity entity, HusbandryAnimal animal) {
        if (player == null || entity == null || animal == null) {
            return;
        }
        boolean mount = HusbandryMounts.isMount(entity);
        HusbandryInspectHolder holder = new HusbandryInspectHolder(animal.uuid(), HusbandryIdentity.session(player).orElse(null));
        String name = displayName(animal, entity.getType());
        Inventory inv = Bukkit.createInventory(holder, mount ? 36 : 27, Component.text(name, GOLD));
        holder.setInventory(inv);

        inv.setItem(OWNERS_SLOT, ownersItem(animal.uuid()));
        inv.setItem(NAME_SLOT, item(Material.NAME_TAG, Component.text(name, GOLD),
                line(HusbandryEntities.displayName(entity.getType()), NamedTextColor.GRAY),
                line(growingLine(animal), NamedTextColor.GRAY)));
        ItemStack products = productsItem(entity.getType(), HusbandryGuiBars.yieldPercent(animal));
        if (products != null) {
            inv.setItem(PRODUCTS_SLOT, products);
        }
        inv.setItem(STATUS_SLOT, statusItem(animal, mount));
        inv.setItem(NEUTERED_SLOT, item(Material.SHEARS,
                Component.text(animal.neutered() ? "Neutered" : "Not neutered",
                        animal.neutered() ? NamedTextColor.RED : NamedTextColor.GREEN),
                line(animal.neutered() ? "It cannot breed." : "It can breed when grown and happy.", NamedTextColor.GRAY)));

        int careMax = HusbandryConfig.careMax();
        int hearts = HusbandryTrust.filledHearts(animal.care());
        for (int i = 0; i < SEGMENTS; i++) {
            inv.setItem(TRUST_START + i, item(i < hearts ? Material.RED_DYE : Material.GRAY_DYE,
                    Component.text("Trust ", TRUST).append(HusbandryTrust.hearts(animal.care())),
                    line(animal.care() + "/" + careMax, NamedTextColor.GRAY),
                    line(mount ? "Feed, brush and ride it each day." : "Feed, brush and pet it each day.",
                            NamedTextColor.DARK_GRAY)));
        }

        int maxGenetics = HusbandryConfig.maxGenetics();
        int filled = HusbandryGuiBars.filledSegments(animal.genetics(), maxGenetics);
        Component stars = stars(HusbandryConfig.starsForGenetics(animal.genetics()));
        for (int i = 0; i < SEGMENTS; i++) {
            inv.setItem(GENETICS_START + i, item(i < filled ? Material.SLIME_SPAWN_EGG : Material.VEX_SPAWN_EGG,
                    Component.text("Genetics ", GENES).append(stars),
                    line(animal.genetics() + "/" + maxGenetics, NamedTextColor.GRAY),
                    line("Inherited from its parents.", NamedTextColor.DARK_GRAY)));
        }

        if (mount) {
            inv.setItem(HEALTH_SLOT, statItem(Material.GOLDEN_APPLE, "Health",
                    String.format("%.1f hearts", HusbandryMounts.healthHearts(entity)), animal, HusbandryMountGenes.Stat.HEALTH));
            inv.setItem(SPEED_SLOT, statItem(Material.FEATHER, "Speed",
                    String.format("%.2f blocks/s", HusbandryMounts.speedBlocksPerSecond(entity)), animal, HusbandryMountGenes.Stat.SPEED));
            inv.setItem(JUMP_SLOT, statItem(Material.LEATHER_BOOTS, "Jump",
                    String.format("%.2f blocks", HusbandryMounts.jumpBlockHeight(entity)), animal, HusbandryMountGenes.Stat.JUMP));
        }

        if (HusbandryOwnershipService.isOwner(player, animal.uuid())) {
            inv.setItem(removeSlot(inv), removeItem(false));
        }
        player.openInventory(inv);
    }

    /** The give-up button; once clicked it asks for a second click before anything happens. */
    static ItemStack removeItem(boolean confirming) {
        return confirming
                ? item(Material.WRITABLE_BOOK, Component.text("Click again to give it up", NamedTextColor.RED),
                        line("It will no longer be yours.", NamedTextColor.GRAY))
                : item(Material.WRITABLE_BOOK, Component.text("Remove ownership", NamedTextColor.DARK_RED),
                        line("Stop owning this animal.", NamedTextColor.GRAY));
    }

    static String displayName(HusbandryAnimal animal, EntityType type) {
        String name = animal.name();
        if (name == null || name.isBlank() || "???".equals(name.trim())) {
            return HusbandryEntities.displayName(type);
        }
        return name;
    }

    private static ItemStack statItem(Material material, String label, String value, HusbandryAnimal animal,
            HusbandryMountGenes.Stat stat) {
        return item(material, Component.text(label + " ", GOLD).append(Component.text(value, NamedTextColor.WHITE)),
                line("Gene " + HusbandryMountGenes.gene(animal, stat) + "/" + HusbandryConfig.maxGenetics(),
                        NamedTextColor.GRAY));
    }

    private static ItemStack productsItem(EntityType type, int yieldPct) {
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species == null || !HusbandryProducts.hasProducts(species)) {
            return null;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(line("Yield " + yieldPct + "%", NamedTextColor.GREEN));
        for (String mode : HusbandryProducts.modeLines(species)) {
            lore.add(line(mode, NamedTextColor.GRAY));
        }
        lore.add(line("Genetics set the quality, trust the amount.", NamedTextColor.DARK_GRAY));
        return item(Material.CHEST, Component.text("Products", GOLD), lore.toArray(Component[]::new));
    }

    private static String growingLine(HusbandryAnimal animal) {
        if (HusbandryGrowth.isMature(animal, System.currentTimeMillis())) {
            return "Grown up";
        }
        long remaining = Math.max(0L, animal.matureAt() - System.currentTimeMillis());
        return "Grows up in " + TimeFormatter.formatTime((int) (remaining / 1000L));
    }

    private static ItemStack statusItem(HusbandryAnimal animal, boolean mount) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (!hungry && !dirty) {
            return item(Material.TOTEM_OF_UNDYING, Component.text("Happy", NamedTextColor.GREEN),
                    line(mount ? "A ride a day keeps its trust growing." : "A pet a day keeps its trust growing.",
                            NamedTextColor.GRAY));
        }
        List<Component> lore = new ArrayList<>();
        if (hungry) {
            lore.add(line("Hungry: feed it Universal Feed.", NamedTextColor.GOLD));
        }
        if (dirty) {
            lore.add(line("Dirty: brush it with a Glove.", NamedTextColor.YELLOW));
        }
        lore.add(decayLine(animal));
        return item(Material.ROTTEN_FLESH, Component.text("Needs care", NamedTextColor.RED), lore.toArray(Component[]::new));
    }

    private static Component decayLine(HusbandryAnimal animal) {
        Long hungry = animal.hungrySince();
        Long dirty = animal.dirtySince();
        long earliest = hungry == null ? dirty : (dirty == null ? hungry : Math.min(hungry, dirty));
        long decayStart = earliest + HusbandryConfig.decayGraceSeconds() * 1000L;
        long now = System.currentTimeMillis();
        if (now < decayStart) {
            double hours = (decayStart - now) / (double) MILLIS_PER_HOUR;
            return line("It starts losing trust in " + String.format("%.1f", Math.max(0, hours)) + "h.", NamedTextColor.YELLOW);
        }
        return line("Losing trust: -" + trim(HusbandryConfig.careDownAmount()) + " every "
                + TimeFormatter.formatTime(HusbandryConfig.careDownIntervalSeconds()) + ".", NamedTextColor.RED);
    }

    private static ItemStack ownersItem(UUID animalUuid) {
        List<Component> lore = new ArrayList<>();
        for (HusbandryOwner owner : HusbandryOwnershipService.listOwners(animalUuid)) {
            lore.add(line(owner.role() + ": " + HusbandryIdentity.name(owner.ref()), NamedTextColor.GRAY));
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository != null && repository.hasHeldOwner(animalUuid)) {
            lore.add(line("Unresolved owner slot (staff review)", NamedTextColor.GRAY));
        }
        if (lore.isEmpty()) {
            lore.add(line("Nobody yet: tame it with a named token.", NamedTextColor.GRAY));
        }
        return item(Material.PLAYER_HEAD, Component.text("Owners", NamedTextColor.YELLOW), lore.toArray(Component[]::new));
    }

    private static Component stars(int stars) {
        int clamped = Math.max(0, Math.min(5, stars));
        return Component.text("★".repeat(clamped), GOLD).append(Component.text("★".repeat(5 - clamped), NamedTextColor.DARK_GRAY));
    }

    private static String trim(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color);
    }

    private static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (Component component : lore) {
            lines.add(component.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
