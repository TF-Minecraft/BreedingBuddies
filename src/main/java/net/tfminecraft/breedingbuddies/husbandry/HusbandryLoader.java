package net.tfminecraft.breedingbuddies.husbandry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import net.tfminecraft.tlibs.utils.TimeFormatter;

public final class HusbandryLoader {

    public void load(File file) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            ex.printStackTrace();
            applyDefaults();
            return;
        }

        HusbandryConfig.apply(
                config.getInt("max-animals", 15),
                config.getInt("care-max", 200),
                HusbandryDuration.parseCareIntervalSeconds(config, "care.down", "care-down-per-hour", 3600),
                HusbandryDuration.parseCareAmount(config, "care.down", "care-down-per-hour", 1),
                HusbandryDuration.parseSeconds(config, "decay-grace", "decay-grace-hours", 3600, 86400),
                HusbandryDuration.parseSeconds(config, "long-unload-force", "long-unload-force-hours", 3600, 28800),
                HusbandryDuration.parseSeconds(config, "min-loaded", "min-loaded-seconds", 1, 60),
                HusbandryDuration.parseAfflictionHours(config, "mean", "affliction-mean-hours", 6),
                HusbandryDuration.parseAfflictionHours(config, "min", "affliction-min-hours", 4),
                HusbandryDuration.parseAfflictionHours(config, "max", "affliction-max-hours", 8),
                parseMilkTimerSeconds(config),
                config.getInt("initial-genetic-max", 20),
                config.getInt("max-genetics", 1000),
                config.getInt("min-roast-cuts", 1),
                HusbandryDuration.parseSeconds(config, "wool-timer", "wool-timer-hours", 3600, 28800),
                HusbandryDuration.parseSeconds(config, "grow-up", null, 1, 3600),
                HusbandryDuration.parseSeconds(config, "shed-timer", null, 1, 28800),
                config.getDouble("shed-chance", 0.15),
                HusbandryDuration.parseSeconds(config, "egg-timer", null, 1, 3600),
                config.getString("items.tame", ""),
                config.getString("items.co-own", ""),
                config.getString("items.feed", ""),
                config.getString("items.glove", ""),
                config.getString("items.neuter", ""),
                config.getString("items.inspect", ""),
                config.getString("items.mount-stats", ""),
                parseEntityTypes(config.getStringList("remove-unowned")),
                parseSpecies(config.getConfigurationSection("species")),
                config.getBoolean("damage.other-players", true),
                config.getBoolean("damage.owner", true),
                config.getBoolean("damage.mobs", true),
                config.getBoolean("damage.environment", true),
                parseQualityBands(config.getMapList("quality-from-genetics")),
                parseMounts(config.getConfigurationSection("mounts")),
                config.getDouble("breeding.genetic-variance-multiplier", 1),
                config.getDouble("breeding.genetic-slowdown-divisor", 1),
                parseAmountBands(config.getMapList("amount-from-genetics")));
        HusbandryConfig.setMountShares(
                mountShare(config, "min-pct", 0.40),
                mountShare(config, "genetics-pct", 0.50),
                mountShare(config, "care-pct", 0.10));
        HusbandryConfig.setBreeding(
                config.getDouble("breeding.genetic-variance-multiplier", 0.4),
                config.getDouble("breeding.genetic-slowdown-divisor", 0.4),
                config.getDouble("breeding.care-influence", 0.02));
        HusbandryConfig.setStatsRevision(config.getString("stats-revision", "1"));
        HusbandryConfig.setRestoreLostAnimals(config.getBoolean("restore-lost-animals", true));
        HusbandryConfig.setTrust(
                config.getInt("trust.feed", 10),
                config.getInt("trust.glove", 10),
                config.getInt("trust.pet", 5),
                HusbandryDuration.parseSeconds(config, "trust.pet-cooldown", null, 1, 72000));
        HusbandryConfig.setProfessionExp(
                config.getString("profession", "farming"),
                parseExpBracket(config.getConfigurationSection("exp"), 6, 8));
    }

    private static Set<EntityType> parseEntityTypes(List<String> raw) {
        Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String entry : raw) {
            EntityType type = parseEntityType(entry);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private static Map<EntityType, HusbandrySpecies> parseSpecies(ConfigurationSection section) {
        Map<EntityType, HusbandrySpecies> species = new EnumMap<>(EntityType.class);
        if (section == null) {
            return species;
        }
        for (String key : section.getKeys(false)) {
            EntityType type = parseEntityType(key);
            if (type == null) {
                continue;
            }
            ConfigurationSection slaughter = section.getConfigurationSection(key + ".slaughter");
            ConfigurationSection shear = section.getConfigurationSection(key + ".shear");
            ConfigurationSection shed = section.getConfigurationSection(key + ".shed");
            species.put(type, new HusbandrySpecies(
                    type,
                    section.getBoolean(key + ".milk", false),
                    slaughter != null ? slaughter.getString("meat", "") : "",
                    slaughter != null
                            ? parseDropTable(slaughter.getConfigurationSection("drops"))
                            : HusbandryDropTable.empty(),
                    shear != null
                            ? parseDropTable(shear.getConfigurationSection("drops"))
                            : HusbandryDropTable.empty(),
                    shed != null
                            ? parseDropTable(shed.getConfigurationSection("drops"))
                            : HusbandryDropTable.empty(),
                    section.getString(key + ".egg", ""),
                    parseSpeciesGrowUp(section, key),
                    parseSpeciesDuration(section, key, "wool-timer"),
                    parseSpeciesDuration(section, key, "milk-timer"),
                    parseOptionalExp(section.getConfigurationSection(key + ".exp"))));
        }
        return species;
    }

    private static HusbandryDropTable parseDropTable(ConfigurationSection section) {
        if (section == null) {
            return HusbandryDropTable.empty();
        }
        return new HusbandryDropTable(
                parseDropEntries(section.getMapList("common")),
                parseDropEntries(section.getMapList("rare")),
                parseDropEntries(section.getMapList("epic")),
                parseDropEntries(section.getMapList("legendary")),
                "counted".equalsIgnoreCase(section.getString("mode", "")));
    }

    private static List<HusbandryDropEntry> parseDropEntries(List<Map<?, ?>> raw) {
        List<HusbandryDropEntry> entries = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object pathRaw = entry.get("path");
            String path = pathRaw == null ? "" : String.valueOf(pathRaw).trim();
            int weight = intValue(entry.get("weight"), 1);
            if (weight <= 0) {
                continue;
            }
            entries.add(new HusbandryDropEntry(
                    path,
                    intValue(entry.get("amount"), 1),
                    weight));
        }
        return entries;
    }

    private static int parseSpeciesGrowUp(ConfigurationSection section, String key) {
        return parseSpeciesDuration(section, key, "grow-up");
    }

    private static int parseMilkTimerSeconds(FileConfiguration config) {
        if (config.contains("milk-timer")) {
            return TimeFormatter.parseSeconds(config.getString("milk-timer"));
        }
        return HusbandryDuration.parseSeconds(config, "milk-cooldown", "milk-cooldown-minutes", 60, 1200);
    }

    private static int parseSpeciesDuration(ConfigurationSection section, String key, String field) {
        if (section == null || !section.contains(key + "." + field)) {
            return 0;
        }
        return Math.max(1, TimeFormatter.parseSeconds(section.getString(key + "." + field)));
    }

    private static List<HusbandryQualityBand> parseQualityBands(List<Map<?, ?>> raw) {
        List<HusbandryQualityBand> bands = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            int min = intValue(entry.get("min"), 0);
            int stars = intValue(entry.get("stars"), 1);
            bands.add(new HusbandryQualityBand(min, Math.max(1, Math.min(5, stars))));
        }
        bands.sort(Comparator.comparingInt(HusbandryQualityBand::minGenetics));
        return bands;
    }

    private static List<HusbandryAmountBand> parseAmountBands(List<Map<?, ?>> raw) {
        List<HusbandryAmountBand> bands = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            bands.add(new HusbandryAmountBand(
                    intValue(entry.get("min"), 0),
                    Math.max(1, intValue(entry.get("roast-cuts"), 1)),
                    Math.max(1, intValue(entry.get("wool"), 1)),
                    intValue(entry.get("secondary-extra"), 0)));
        }
        bands.sort(Comparator.comparingInt(HusbandryAmountBand::minGenetics));
        return bands;
    }

    /** Mount stat shares live in mounts.shares; Cooking named them mounts.speed although they apply to every stat. */
    private static double mountShare(FileConfiguration config, String key, double fallback) {
        if (config.contains("mounts.shares." + key)) {
            return config.getDouble("mounts.shares." + key);
        }
        if (config.contains("mounts.speed." + key)) {
            Bukkit.getLogger().warning("[BreedingBuddies] config.yml uses mounts.speed." + key
                    + "; rename it to mounts.shares." + key + ", which applies to health, speed and jump.");
            return config.getDouble("mounts.speed." + key);
        }
        return fallback;
    }

    private static Map<EntityType, HusbandryMountStats> parseMounts(ConfigurationSection section) {
        Map<EntityType, HusbandryMountStats> mounts = new EnumMap<>(EntityType.class);
        if (section == null) {
            return mounts;
        }
        for (String key : section.getKeys(false)) {
            if ("shares".equalsIgnoreCase(key)
                    || "speed".equalsIgnoreCase(key)
                    || "nerf".equalsIgnoreCase(key)
                    || "nerf-divisor".equalsIgnoreCase(key)) {
                continue;
            }
            EntityType type = parseEntityType(key);
            ConfigurationSection stats = section.getConfigurationSection(key);
            if (type == null || stats == null) {
                continue;
            }
            mounts.put(type, new HusbandryMountStats(
                    type,
                    stats.getDouble("min-health", 15),
                    stats.getDouble("max-health", 30),
                    stats.getDouble("min-speed", 0.1125),
                    stats.getDouble("max-speed", 0.3375),
                    stats.getDouble("min-jump", 0.4),
                    stats.getDouble("max-jump", 1.0)));
        }
        return mounts;
    }

    private static EntityType parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Bukkit.getLogger().warning("[BreedingBuddies] Invalid husbandry entity type: " + raw);
            return null;
        }
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                Bukkit.getLogger().warning("[BreedingBuddies] Invalid husbandry number: " + raw);
            }
        }
        return fallback;
    }

    private static HusbandryExpBracket parseExpBracket(ConfigurationSection section, int defaultMin, int defaultMax) {
        if (section == null) {
            return HusbandryExpBracket.of(defaultMin, defaultMax);
        }
        return HusbandryExpBracket.of(section.getInt("min", defaultMin), section.getInt("max", defaultMax));
    }

    private static HusbandryExpBracket parseOptionalExp(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return HusbandryExpBracket.of(section.getInt("min", 6), section.getInt("max", 8));
    }

    private static void applyDefaults() {
        HusbandryConfig.apply(
                15,
                200,
                3600,
                1,
                86400,
                28800,
                60,
                6,
                4,
                8,
                1200,
                20,
                1000,
                1,
                28800,
                3600,
                28800,
                0.15,
                3600,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                Set.of(),
                Map.of(),
                true,
                true,
                true,
                true,
                List.of(new HusbandryQualityBand(0, 1)),
                Map.of(),
                1,
                1,
                List.of(new HusbandryAmountBand(0, 1, 1)));
        HusbandryConfig.setMountShares(0.40, 0.50, 0.10);
        HusbandryConfig.setBreeding(0.4, 0.4, 0.02);
        HusbandryConfig.setStatsRevision("1");
        HusbandryConfig.setRestoreLostAnimals(true);
        HusbandryConfig.setTrust(10, 10, 5, 72000);
        HusbandryConfig.setProfessionExp("farming", HusbandryExpBracket.of(6, 8));
    }
}
