package net.tfminecraft.breedingbuddies.configurables;

import org.bukkit.configuration.file.FileConfiguration;

public class Numbers {
	public static int maxFriendshipAndGenetics;
    public static int initialGeneticMax;
    public static int friendshipByFeeding;
    public static int friendshipByCaring;
    public static int friendshipLostNotFeeding;
    public static int friendshipLostNotCaring;
    public static int maxAnimalsPerChunk;
    public static int maxIrlDaysOut;
    public static int maxIrlDaysChunkAbandoned;
    public static double geneticVarianceMultiplier;
    public static double friendshipInfluenceMultiplier;
    public static double geneticDivisorMultiplierToRetardProgression;
    public static double hoursBetweenRewards;
    public static int startingDayHour;
    public static int startingDayMin;

    public static int minMountHealth;
    public static double minMountSpeed;

    public static double minMountJump;
    public static double mountNerfDivisor;
    public static boolean nerfMountStats;
    static FileConfiguration config;

    public static void loadConfig(FileConfiguration numbersConfig) {
        config = numbersConfig;
        maxFriendshipAndGenetics = numbersConfig.getInt("maxFriendshipAndGenetics");
        initialGeneticMax = numbersConfig.getInt("initialGeneticMax");
        friendshipByFeeding = numbersConfig.getInt("friendshipByFeeding");
        friendshipByCaring = numbersConfig.getInt("friendshipByCaring");
        friendshipLostNotFeeding = numbersConfig.getInt("friendshipLostNotFeeding");
        friendshipLostNotCaring = numbersConfig.getInt("friendshipLostNotCaring");
        maxAnimalsPerChunk = numbersConfig.getInt("maxAnimalsPerChunk");
        maxIrlDaysOut = numbersConfig.getInt("maxIrlDaysOut");
        maxIrlDaysChunkAbandoned = numbersConfig.getInt("maxIrlDaysChunkAbandoned");
        geneticVarianceMultiplier = numbersConfig.getDouble("geneticVarianceMultiplier");
        friendshipInfluenceMultiplier = numbersConfig.getDouble("friendshipInfluenceMultiplier");
        geneticDivisorMultiplierToRetardProgression = numbersConfig.getDouble("geneticDivisorMultiplierToRetardProgression");
        hoursBetweenRewards = numbersConfig.getDouble("hoursBetweenRewards");
        startingDayHour = numbersConfig.getInt("startingDayHour");
        startingDayMin = numbersConfig.getInt("startingDayMin");
        mountNerfDivisor = numbersConfig.getDouble("mountNerfDivisor");
        nerfMountStats = numbersConfig.getBoolean("nerfMountStats");
    }
}
