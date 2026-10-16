package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Trust, stored as care and shown as hearts, grows only through what owners do for their animals:
 * feeding a hungry animal, brushing a dirty one and a daily pet (riding counts for mounts).
 */
public final class HusbandryTrust {

    public enum Gesture { FEED, GLOVE, PET }

    static final int HEARTS = 5;
    private static final TextColor FULL = TextColor.color(0xFF5577);
    private static final TextColor EMPTY = TextColor.color(0x555555);

    private HusbandryTrust() {}

    /** Adds trust for an owner's gesture and returns how much was added; a pet counts once per cooldown. */
    public static int reward(HusbandryAnimal animal, Gesture gesture, long nowMillis) {
        if (animal == null || gesture == null) {
            return 0;
        }
        if (gesture == Gesture.PET) {
            if (!canPet(animal, nowMillis)) {
                return 0;
            }
            animal.setLastPetAt(nowMillis);
        }
        int before = animal.care();
        animal.setCare(Math.max(0, Math.min(HusbandryConfig.careMax(), before + HusbandryConfig.trustFor(gesture))));
        return animal.care() - before;
    }

    public static boolean canPet(HusbandryAnimal animal, long nowMillis) {
        Long last = animal.lastPetAt();
        return last == null || nowMillis - last >= HusbandryConfig.petCooldownSeconds() * 1000L;
    }

    /** Filled hearts out of five, rounded; any trust at all shows at least one heart. */
    public static int filledHearts(int trust) {
        int max = HusbandryConfig.careMax();
        if (trust <= 0 || max <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(HEARTS, (int) Math.round(HEARTS * (double) trust / max)));
    }

    public static Component hearts(int trust) {
        int filled = filledHearts(trust);
        return Component.text("❤".repeat(filled), FULL).append(Component.text("❤".repeat(HEARTS - filled), EMPTY));
    }

    /** Shows the animal's name and hearts above the hotbar, with a little sparkle when trust grew. */
    static void show(Player player, LivingEntity entity, HusbandryAnimal animal, int gained) {
        String name = animal.name() == null || animal.name().isBlank() ? HusbandryEntities.displayName(entity.getType()) : animal.name();
        player.sendActionBar(Component.text(name + " ", NamedTextColor.GOLD).append(hearts(animal.care())));
        if (gained <= 0) {
            return;
        }
        entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, entity.getHeight() + 0.3, 0),
                5, 0.4, 0.2, 0.4, 0.0);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
    }
}
