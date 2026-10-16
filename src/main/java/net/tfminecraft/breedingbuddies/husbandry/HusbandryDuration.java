package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.utils.TimeFormatter;

final class HusbandryDuration {

    private static final java.util.Set<String> LEGACY_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private HusbandryDuration() {}

    static int parseSeconds(FileConfiguration config, String key, String legacyKey, int legacyMultiplier, int fallback) {
        if (config.contains(key)) {
            return TimeFormatter.parseSeconds(config.getString(key));
        }
        if (legacyKey != null && config.contains(legacyKey)) {
            warnLegacy(legacyKey, key);
            return (int) Math.round(config.getDouble(legacyKey) * legacyMultiplier);
        }
        return fallback;
    }

    static double parseHours(FileConfiguration config, String key, String legacyKey, double fallback) {
        if (config.contains(key)) {
            return TimeFormatter.parseSeconds(config.getString(key)) / 3600.0;
        }
        if (legacyKey != null && config.contains(legacyKey)) {
            warnLegacy(legacyKey, key);
            return config.getDouble(legacyKey);
        }
        return fallback;
    }

    static double parseHours(ConfigurationSection section, String key, String legacyKey, double fallback) {
        if (section != null && section.contains(key)) {
            return TimeFormatter.parseSeconds(section.getString(key)) / 3600.0;
        }
        if (section != null && legacyKey != null && section.contains(legacyKey)) {
            warnLegacy(legacyKey, key);
            return section.getDouble(legacyKey);
        }
        return fallback;
    }

    static double parseAfflictionHours(FileConfiguration config, String subKey, String legacyKey, double fallback) {
        ConfigurationSection section = config.getConfigurationSection("affliction");
        if (section != null && section.contains(subKey)) {
            return TimeFormatter.parseSeconds(section.getString(subKey)) / 3600.0;
        }
        if (legacyKey != null && config.contains(legacyKey)) {
            warnLegacy(legacyKey, "affliction." + subKey);
            return config.getDouble(legacyKey);
        }
        return fallback;
    }

    static int parseCareIntervalSeconds(FileConfiguration config, String path, String legacyKey, int fallback) {
        if (config.contains(path + ".interval")) {
            return Math.max(1, TimeFormatter.parseSeconds(config.getString(path + ".interval")));
        }
        if (legacyKey != null && config.contains(legacyKey)) {
            warnLegacy(legacyKey, path + ".interval");
            return 3600;
        }
        return fallback;
    }

    static double parseCareAmount(FileConfiguration config, String path, String legacyKey, double fallback) {
        if (config.contains(path + ".amount")) {
            return config.getDouble(path + ".amount");
        }
        if (legacyKey != null && config.contains(legacyKey)) {
            warnLegacy(legacyKey, path + ".amount");
            return config.getDouble(legacyKey);
        }
        return fallback;
    }

    private static void warnLegacy(String legacyKey, String newKey) {
        if (LEGACY_WARNED.add(legacyKey)) {
            Bukkit.getLogger().warning("[BreedingBuddies] config.yml uses deprecated key '" + legacyKey
                    + "'; prefer '" + newKey + "' with TLibs duration strings (e.g. 8h, 20m).");
        }
    }
}
