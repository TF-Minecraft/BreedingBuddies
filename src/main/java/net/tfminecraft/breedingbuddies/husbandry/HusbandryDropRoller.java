package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.bukkit.inventory.ItemStack;

public final class HusbandryDropRoller {

    public enum CountMode {
        HIDE_COUNT,
        WOOL_COUNT,
        SINGLE
    }

    private HusbandryDropRoller() {}

    public static List<ItemStack> rollSlaughterExtras(HusbandryAnimal animal, Random random, long nowMillis) {
        if (animal == null) {
            return List.of();
        }
        HusbandrySpecies species = speciesOf(animal);
        if (species == null) {
            return List.of();
        }
        return roll(species.slaughterDrops(), animal, random, nowMillis, CountMode.HIDE_COUNT);
    }

    public static List<ItemStack> rollShearDrops(HusbandryAnimal animal, Random random, long nowMillis) {
        if (animal == null) {
            return List.of();
        }
        HusbandrySpecies species = speciesOf(animal);
        if (species == null) {
            return List.of();
        }
        return roll(species.shearDrops(), animal, random, nowMillis, CountMode.WOOL_COUNT);
    }

    public static List<ItemStack> rollShedDrops(HusbandryAnimal animal, Random random) {
        if (animal == null) {
            return List.of();
        }
        HusbandrySpecies species = speciesOf(animal);
        if (species == null) {
            return List.of();
        }
        CountMode countMode = species.shedDrops().counted() ? CountMode.WOOL_COUNT : CountMode.SINGLE;
        return roll(species.shedDrops(), animal, random, System.currentTimeMillis(), countMode);
    }

    public static List<ItemStack> roll(
            HusbandryDropTable table,
            HusbandryAnimal animal,
            Random random,
            long nowMillis,
            CountMode countMode) {
        if (animal == null || random == null || table == null || table.isEmpty()) {
            return List.of();
        }
        if (table.counted()) {
            int count = resolveCount(animal, nowMillis, countMode);
            if (count <= 0) {
                return List.of();
            }
            List<HusbandryDropEntry> pool = unlockedPool(table, HusbandryConfig.starsForGenetics(animal.genetics()));
            List<ItemStack> drops = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                HusbandryDropEntry picked = pickEntry(pool, random);
                if (picked == null || picked.path().isBlank()) {
                    continue;
                }
                ItemStack stack = HusbandryHarvest.buildTlibs(picked.path(), Math.max(1, picked.amount()));
                if (stack != null) {
                    drops.add(stack);
                }
            }
            return drops;
        }
        return rollExtras(table, animal, random).map(List::of).orElse(List.of());
    }

    public static Optional<ItemStack> rollExtras(
            HusbandryDropTable table,
            HusbandryAnimal animal,
            Random random) {
        if (animal == null || random == null || table == null || table.isEmpty()) {
            return Optional.empty();
        }
        List<HusbandryDropEntry> pool = unlockedPool(table, HusbandryConfig.starsForGenetics(animal.genetics()));
        HusbandryDropEntry picked = pickEntry(pool, random);
        if (picked == null || picked.path().isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(HusbandryHarvest.buildTlibs(picked.path(), picked.amount()));
    }

    private static int resolveCount(HusbandryAnimal animal, long nowMillis, CountMode countMode) {
        return switch (countMode) {
            case HIDE_COUNT -> hideCount(animal, nowMillis);
            case WOOL_COUNT -> HusbandryConfig.woolFor(HusbandryConfig.effectiveGenetics(animal));
            case SINGLE -> 1;
        };
    }

    public static int hideCount(HusbandryAnimal animal, long nowMillis) {
        if (animal == null) {
            return 0;
        }
        if (woolBlocked(animal.type(), animal, nowMillis)) {
            return 0;
        }
        return HusbandryConfig.hideCount(HusbandryConfig.effectiveGenetics(animal));
    }

    public static boolean woolBlocked(String typeName, HusbandryAnimal animal, long nowMillis) {
        if (typeName == null) {
            return false;
        }
        if (!typeName.equalsIgnoreCase("SHEEP") && !typeName.equalsIgnoreCase("GOAT")) {
            return false;
        }
        Long readyAt = animal == null ? null : animal.woolReadyAt();
        return readyAt != null && readyAt > nowMillis;
    }

    public static List<HusbandryDropEntry> unlockedPool(HusbandryDropTable table, int stars) {
        List<HusbandryDropEntry> pool = new ArrayList<>();
        if (table == null) {
            return pool;
        }
        pool.addAll(table.common());
        if (stars >= 3) {
            pool.addAll(table.rare());
        }
        if (stars >= 4) {
            pool.addAll(table.epic());
        }
        if (stars >= 5) {
            pool.addAll(table.legendary());
        }
        return pool;
    }

    public static HusbandryDropEntry pickEntry(List<HusbandryDropEntry> pool, Random random) {
        if (pool == null || pool.isEmpty() || random == null) {
            return null;
        }
        long totalWeight = pool.stream().mapToLong(HusbandryDropEntry::weight).sum();
        long roll = totalWeight <= Integer.MAX_VALUE
                ? random.nextInt((int) totalWeight) : random.nextLong(totalWeight);
        for (int index = 0; index < pool.size() - 1; index++) {
            HusbandryDropEntry entry = pool.get(index);
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return pool.getLast();
    }

    private static HusbandrySpecies speciesOf(HusbandryAnimal animal) {
        try {
            return HusbandryConfig.species(org.bukkit.entity.EntityType.valueOf(animal.type()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
