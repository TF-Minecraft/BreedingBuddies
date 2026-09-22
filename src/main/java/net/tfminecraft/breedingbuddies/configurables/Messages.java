package net.tfminecraft.breedingbuddies.configurables;

import org.bukkit.configuration.file.FileConfiguration;

public class Messages {
	public static String inventoryName;
    public static String nameFirst;
    public static String ownerFirst;
    public static String invalidAnimalOrNotFound;
    public static String alreadyOwner;
    public static String nowCoowner;
    public static String ownershipShared;
    public static String useOnOwnedAnimal;
    public static String onlyOwnerCanShare;
    public static String nowOwner;
    public static String notOwner;
    public static String alreadyFed;
    public static String alreadyCared;
    public static String animalsOut;
    public static String spaceNeeded;
    public static String waterNeeded;
    public static String animalsFed;
    public static String animalsCared;
    public static String animalsOwned;
    public static String lacksSpace;
	public static String stableChunk;
	public static String alreadyStableChunk;
	public static String alreadyLinked;
    public static String neuteredHorse;
    public static String tameFirst;
    public static String removeOwnership;

    public static void loadConfig(FileConfiguration config) {
        inventoryName = config.getString("messages.inventoryName");
        nameFirst = config.getString("messages.nameFirst");
        ownerFirst = config.getString("messages.ownerFirst");
        invalidAnimalOrNotFound = config.getString("messages.invalidAnimalOrNotFound");
        alreadyOwner = config.getString("messages.alreadyOwner");
        nowCoowner = config.getString("messages.nowCoowner");
        ownershipShared = config.getString("messages.ownershipShared");
        useOnOwnedAnimal = config.getString("messages.useOnOwnedAnimal");
        onlyOwnerCanShare = config.getString("messages.onlyOwnerCanShare");
        nowOwner = config.getString("messages.nowOwner");
        notOwner = config.getString("messages.notOwner");
        alreadyFed = config.getString("messages.alreadyFed");
        alreadyCared = config.getString("messages.alreadyCared");
        animalsOut = config.getString("messages.animalsOut");
        spaceNeeded = config.getString("messages.spaceNeeded");
        waterNeeded = config.getString("messages.waterNeeded");
        animalsFed = config.getString("messages.animalsFed");
        animalsCared = config.getString("messages.animalsCared");
        animalsOwned = config.getString("messages.animalsOwned");
        lacksSpace = config.getString("messages.lacksSpace");
        stableChunk = config.getString("messages.stableChunk");
        alreadyStableChunk = config.getString("messages.alreadyStableChunk");
        alreadyLinked = config.getString("messages.alreadyLinked");
        neuteredHorse = config.getString("messages.neuteredHorse");
        tameFirst = config.getString("messages.tameFirst");
        removeOwnership = config.getString("messages.removeOwnership");

//        Bukkit.getLogger().info("[BreedingBuddies] Loaded message: nameFirst = " + nameFirst);
    }
}
