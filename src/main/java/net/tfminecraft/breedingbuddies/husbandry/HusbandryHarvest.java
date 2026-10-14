package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.cooking.api.CookingFoodApi;

public final class HusbandryHarvest {

    private HusbandryHarvest() {}

    public static void prepareNewAnimal(HusbandryAnimal animal, EntityType type) {
        if (animal == null) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species != null && species.canShear()) {
            animal.setWoolReadyAt(System.currentTimeMillis());
        }
        if (species != null && species.canShed()) {
            animal.setShedReadyAt(System.currentTimeMillis());
        }
        if (species != null && species.hasEgg()) {
            animal.setEggReadyAt(System.currentTimeMillis());
        }
    }

    public static int stars(HusbandryAnimal animal) {
        if (animal == null) {
            return 1;
        }
        return HusbandryConfig.starsForGenetics(animal.genetics());
    }

    public static int rollQuality(HusbandryAnimal animal, Random random) {
        return HusbandryQualityRange.roll(HusbandryConfig.qualityRange(animal), random);
    }

    public enum ShearResult {
        IMMATURE,
        COOLDOWN,
        DONE
    }

    public static ShearResult shearReadiness(
            HusbandryAnimal animal,
            HusbandrySpecies species,
            long nowMillis) {
        if (animal == null || species == null || !species.canShear()) {
            return ShearResult.COOLDOWN;
        }
        if (!HusbandryGrowth.isMature(animal, nowMillis)) {
            return ShearResult.IMMATURE;
        }
        if (animal.woolReadyAt() != null && animal.woolReadyAt() > nowMillis) {
            return ShearResult.COOLDOWN;
        }
        return ShearResult.DONE;
    }

    public static ShearResult tryShear(
            Player player,
            LivingEntity living,
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        ShearResult readiness = shearReadiness(animal, species, nowMillis);
        if (readiness == ShearResult.IMMATURE) {
            if (player != null) {
                player.sendMessage("§cThis animal is still growing up.");
            }
            return ShearResult.IMMATURE;
        }
        if (readiness != ShearResult.DONE) {
            if (player != null) {
                player.sendMessage("§cThis animal is not ready to be sheared.");
            }
            return ShearResult.COOLDOWN;
        }
        grantShearExtras(player, animal, nowMillis);
        if (living != null && living.getWorld() != null) {
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
        }
        startWoolTimer(animal, species, repository, nowMillis);
        return ShearResult.DONE;
    }

    public static ShearResult trySheepBonusShear(
            Player player,
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        ShearResult readiness = shearReadiness(animal, species, nowMillis);
        if (readiness != ShearResult.DONE) {
            return readiness;
        }
        grantShearExtras(player, animal, nowMillis);
        startWoolTimer(animal, species, repository, nowMillis);
        return ShearResult.DONE;
    }

    private static void grantShearExtras(Player player, HusbandryAnimal animal, long nowMillis) {
        if (player == null) {
            return;
        }
        for (ItemStack drop : HusbandryDropRoller.rollShearDrops(animal, ThreadLocalRandom.current(), nowMillis)) {
            ItemStack overflow = CookingFoodApi.addToInventory(player, drop);
            if (overflow != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
    }

    private static void startWoolTimer(
            HusbandryAnimal animal,
            HusbandrySpecies species,
            HusbandryRepository repository,
            long nowMillis) {
        long wait = HusbandryConfig.woolTimerSeconds(species == null ? null : species.type()) * 1000L;
        animal.setWoolReadyAt(nowMillis + wait);
        if (repository != null) {
            repository.upsertAnimal(animal);
        }
    }

    public static Sound milkSound(EntityType type) {
        if (type == EntityType.GOAT) {
            return Sound.ENTITY_GOAT_MILK;
        }
        return Sound.ENTITY_COW_MILK;
    }

    public static ItemStack buildMilk(HusbandryAnimal animal, EntityType type) {
        if (animal == null) {
            return null;
        }
        return CookingFoodApi.buildMilk(type, rollQuality(animal, ThreadLocalRandom.current()));
    }

    public static ItemStack buildFood(HusbandryAnimal animal, String foodString) {
        if (animal == null) {
            return null;
        }
        int requested = HusbandryConfig.roastCutsFor(HusbandryConfig.effectiveGenetics(animal));
        return CookingFoodApi.buildRoast(foodString, rollQuality(animal, ThreadLocalRandom.current()), requested);
    }

    public static ItemStack buildTlibs(String path, int amount) {
        if (path == null || path.isBlank() || "vanilla".equalsIgnoreCase(path)) {
            return null;
        }
        try {
            ItemStack stack = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            if (stack == null || stack.getType().isAir()) {
                return null;
            }
            stack.setAmount(Math.max(1, Math.min(64, amount)));
            return stack;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isWoolDrop(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String name = stack.getType().name();
        return name.endsWith("_WOOL") || name.equals("WOOL") || stack.getType() == Material.WHITE_WOOL;
    }
}
