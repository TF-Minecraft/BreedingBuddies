package net.tfminecraft.breedingbuddies.configurables;

import org.bukkit.configuration.file.FileConfiguration;

public class CustomItems {
	public static String tamingItem;
    public static String coownershipItem;
    public static String universalFeed;
    public static String caringItem;
    public static String farmReportItem;
    public static String stableChunkItem;
    public static String collectorsItem;
    public static String mountStatsItem;
    public static String neuterItem;


    public static void loadConfig(FileConfiguration config) {
        tamingItem = config.getString("items.tamingItem", "minecraft:carrot");
        coownershipItem = config.getString("items.coownershipItem", "minecraft:paper");
        universalFeed = config.getString("items.universalFeed", "minecraft:wheat");
        caringItem = config.getString("items.caringItem", "minecraft:golden_apple");
        farmReportItem = config.getString("items.farmReportItem", "minecraft:book");
        stableChunkItem = config.getString("items.stableChunkItem", "minecraft:fence");
        collectorsItem = config.getString("items.collectorsItem", "minecraft:hopper");
        mountStatsItem = config.getString("items.mountStatsItem", "minecraft:carrot_on_a_stick");
        neuterItem = config.getString("items.neuterItem", "minecraft:shears");
    }
}
