package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import net.tfminecraft.breedingbuddies.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class BreedingBuddies extends JavaPlugin implements TabCompleter {
    private FileConfiguration bundlesConfig = null;
    private static BreedingBuddies instance;
    private File bundlesFile = null;
    private FileConfiguration itemsConfig = null;
    private File itemsFile = null;
    private FileConfiguration messagesConfig = null;
    private File messagesFile = null;
    private FileConfiguration numbersConfig = null;
    private File numbersFile = null;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private DayChangeScheduler dayScheduler;
    @Override
    public void onEnable() {
        PluginData.loadAllData();
        createAndLoadConfigs();
        registerListeners();
        this.dayScheduler = new DayChangeScheduler(this, Numbers.startingDayHour, Numbers.startingDayMin);
        this.dayScheduler.startScheduler();
        //this.getCommand("breedingbuddies").setExecutor(this);
        InitSubcommands();
        getCommand("breedingbuddies").setTabCompleter(this);
        startAutoSaveTask();
        instance = this;
    }

    @Override
    public void onDisable() {
//    	Cleanser.removeDisappearedAnimals();
    	PluginData.saveAllData(false);
    }

    public static BreedingBuddies getInstance() {
        return instance;
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                PluginData.saveAllData(false);
            }
        }.runTaskTimer(this, 0, 20 * 60 * 10);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("breedingbuddies")) return false;
        if (args.length == 0) return false;

        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /breedingbuddies <reload|spawnanimal|...>");
            return false;
        }

        return subCommand.execute(sender, args);
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        createAndLoadConfigs();

        if (this.dayScheduler != null) {
            this.dayScheduler.cancel();
        }
        this.dayScheduler = new DayChangeScheduler(this, Numbers.startingDayHour, Numbers.startingDayMin);
        this.dayScheduler.startScheduler();

        sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies]" + ChatColor.YELLOW + " Config reloaded successfully!");
        return true;
    }

    private boolean handleStableChunk(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            Chunk chunk = player.getLocation().getChunk();
            boolean isStable = ChunkManager.isStableChunk(chunk);
            sender.sendMessage(ChatColor.GREEN + "Stable chunk: " + isStable);
            return true;
        }
        return false;
    }

    private boolean handleChangeDay(CommandSender sender, String[] args) {
        Bukkit.getServer().getPluginManager().callEvent(new DayChangeEvent());
        sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies]" + ChatColor.YELLOW + " Day changed!");
        return true;
    }

    private boolean handleSpawnAnimal(CommandSender sender, String[] args) {
        if (args.length == 4 && sender instanceof Player player) {
            String entityType = args[1].toUpperCase();
            int friendship = Integer.parseInt(args[2]);
            int genetics = Integer.parseInt(args[3]);

            if (!bundlesConfig.contains(entityType)) {
                sender.sendMessage(ChatColor.RED + "Invalid entity type specified in config.");
                return false;
            }

            EntityType type = EntityType.valueOf(entityType);
            Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);
            UUID entityUuid = entity.getUniqueId();

            FarmAnimal farmAnimal = new FarmAnimal(entityUuid, entityType, player.getUniqueId(), entity, genetics, friendship);
            farmAnimal.setState(AnimalStates.SPAWNED);
            UnownedAnimalsManager.addUnownedAnimal(farmAnimal);

            sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies]" + ChatColor.YELLOW + " Animal spawned successfully!");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /breedingbuddies spawnanimal <entityType> <friendship> <genetics>");
        return false;
    }

    private void InitSubcommands(){
        subCommands.put("reload", this::handleReload);
        subCommands.put("stablechunk", this::handleStableChunk);
        subCommands.put("changeday", this::handleChangeDay);
        subCommands.put("spawnanimal", this::handleSpawnAnimal);
        subCommands.put("spawnmount", this::handleSpawnMount);
        subCommands.put("savedata", this::handleSaveData);
        subCommands.put("loaddata", this::handleLoadData);
        subCommands.put("fix", this::handleFix);
    }
    private boolean handleSpawnMount(CommandSender sender, String[] args) {
        if (args.length == 6 && sender instanceof Player player) {
            String entityType = args[1].toUpperCase();
            int friendship = Integer.parseInt(args[2]);
            double health = Integer.parseInt(args[3]);
            double speed = Double.parseDouble(args[4]);
            double jump = Double.parseDouble(args[5]);

            EntityType type;
            try {
                type = EntityType.valueOf(entityType);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid entity type.");
                return false;
            }

            if (!AbstractHorse.class.isAssignableFrom(type.getEntityClass())) {
                sender.sendMessage(ChatColor.RED + "Entity type must be a mount (like HORSE, DONKEY, etc).");
                return false;
            }

            Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);

            if (!(entity instanceof AbstractHorse horse)) {
                sender.sendMessage(ChatColor.RED + "Spawned entity is not a mount.");
                return false;
            }

            UUID entityUuid = entity.getUniqueId();
            FarmAnimal farmAnimal = new FarmAnimal(entityUuid, entityType, player.getUniqueId(), entity, 0, friendship);
            farmAnimal.setState(AnimalStates.SPAWNED);
            farmAnimal.setFriendshipPoints(friendship);
            UnownedAnimalsManager.addUnownedAnimal(farmAnimal);

            horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            horse.setHealth(health);
            horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
            horse.setJumpStrength(jump);

            NamespacedKey key = new NamespacedKey(BreedingBuddies.getInstance(), "is_nerfed");
            entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (true ? 1 : 0));

            sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies]" + ChatColor.YELLOW + " Animal spawned successfully!");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /breedingbuddies spawnmount <entityType> <friendship> <health> <speed> <jump>");
        return false;
    }

    private boolean handleSaveData(CommandSender sender, String[] args) {
        PluginData.saveAllData(false);
        sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies] Data saved.");
        return true;
    }

    private boolean handleLoadData(CommandSender sender, String[] args) {
        PluginData.loadAllData();
        sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies] Data loaded.");
        return true;
    }

    private boolean handleFix(CommandSender sender, String[] args) {
        for (Set<FarmAnimal> animals : PluginData.getPlayerAnimals().values()) {
            for (FarmAnimal animal : animals) {
                //animal.setCared(true);
                //animal.setFed(true);
                animal.setState(AnimalStates.HAPPY);
            }
        }
        sender.sendMessage(ChatColor.GREEN + "[BreedingBuddies] Animals fixed.");
        return true;
    }

    private interface SubCommand {
        boolean execute(CommandSender sender, String[] args);
    }

    private void createAndLoadConfigs() {
        createAndLoadBundleConfig();
        createAndLoadItemConfig();
        createAndLoadMessageConfig();
        createAndLoadNumbersConfig();
        loadConfigs();
        new AnimalUtils(bundlesConfig);
    }

    private void createAndLoadBundleConfig() {
        bundlesFile = new File(getDataFolder(), "bundles.yaml");
        if (!bundlesFile.exists()) {
            createConfigFile(bundlesFile, getDefaultBundleConfig());
        }
        bundlesConfig = YamlConfiguration.loadConfiguration(bundlesFile);
    }

    private void createAndLoadItemConfig() {
        itemsFile = new File(getDataFolder(), "items.yaml");
        if (!itemsFile.exists()) {
            createConfigFile(itemsFile, getDefaultItemsConfig());
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    private void createAndLoadMessageConfig() {
        messagesFile = new File(getDataFolder(), "messages.yaml");
        if (!messagesFile.exists()) {
            createConfigFile(messagesFile, getDefaultMessageConfig());
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void createAndLoadNumbersConfig() {
        numbersFile = new File(getDataFolder(), "config.yaml");
        if (!numbersFile.exists()) {
            createConfigFile(numbersFile, getDefaultNumbersConfig());
        }
        numbersConfig = YamlConfiguration.loadConfiguration(numbersFile);
    }

    public File getNumbersFile() {
        return numbersFile;
    }

    private void createConfigFile(File file, String content) {
        try {
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfigs() {
        Numbers.loadConfig(numbersConfig);
        Messages.loadConfig(messagesConfig);
        CustomItems.loadConfig(itemsConfig);
        BundleCollector.setBundlesConfig(bundlesConfig);
        DayChangeListener.setNumbersConfig(numbersConfig);
        FarmAnimal.setBundlesConfig(bundlesConfig);
        BundleOpener.setBundlesConfig(bundlesConfig);
        MountStats.setConfigs(bundlesConfig, numbersConfig);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BreedingListener(), this);
        getServer().getPluginManager().registerEvents(new TamingListener(this), this);
        getServer().getPluginManager().registerEvents(new DayChangeListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryManager(), this);
        getServer().getPluginManager().registerEvents(new AnimalCareListener(), this);
        getServer().getPluginManager().registerEvents(new AnimalDeathListener(), this);
        getServer().getPluginManager().registerEvents(new FarmReport(), this);
        getServer().getPluginManager().registerEvents(new ChunkCreation(), this);
        getServer().getPluginManager().registerEvents(new BundleOpener(this), this);
        getServer().getPluginManager().registerEvents(new BundleCollector(this), this);
        getServer().getPluginManager().registerEvents(new AnvilRenameListener(this), this);
        getServer().getPluginManager().registerEvents(new PluginData(), this);
        getServer().getPluginManager().registerEvents(new MountListener(), this);
        getServer().getPluginManager().registerEvents(new NerfMountListener(), this);
    }

    // Default configurations

    private String getDefaultBundleConfig() {
        return """
# ========== HORSE CONFIGURATION ==========
HORSE:
  mountStats:                     # Stats applied when used as a mount
    minHealth: 15                 # Minimum possible health
    maxHealth: 30                 # Maximum possible health
    minSpeed: 0.1125              # Minimum movement speed
    maxSpeed: 0.3375              # Maximum movement speed
    minJump: 0.4                  # Minimum jump height
    maxJump: 1.0                  # Maximum jump height
  bundlesEnabled: false           # Horses do not drop bundles

# ========== COW CONFIGURATION ==========
COW:
  bundlesEnabled: true            # Bundles can be obtained from cows
  bundles:

    # ----- Common Bundle -----
    common:
      bundleItem:                 # The item given to the player
        type: "mmoitem"           # Item comes from MMOItems
        mmoitemType: "PETS"
        mmoitemID: "COMMON_ANIMAL_BUNDLE"
      prizes:                     # Possible rewards from this bundle
        prize1:
          type: "vanilla"         # Minecraft vanilla item
          itemID: "BEEF"
          amount: 4
          weight: 50              # Probability weight
        prize2:
          type: "mmoitem"
          mmoitemType: "MATERIALS"
          mmoitemID: "LEATHER"
          amount: 4
          weight: 50

    # ----- Rare Bundle -----
    rare:
      bundleItem:
        type: "mmoitem"
        mmoitemType: "PETS"
        mmoitemID: "RARE_ANIMAL_BUNDLE"
      prizes:
        prize1:
          type: "vanilla"
          itemID: "BEEF"
          amount: 8
          weight: 45
        prize2:
          type: "mmoitem"
          mmoitemType: "MATERIALS"
          mmoitemID: "LEATHER"
          amount: 8
          weight: 45
        prize3:
          type: "mmoitem"
          mmoitemType: "MATERIALS"
          mmoitemID: "RARE_LEATHER"
          amount: 1
          weight: 5
        prize4:
          type: "mmoitem"
          mmoitemType: "MATERIALS"
          mmoitemID: "EPIC_LEATHER"
          amount: 1
          weight: 5
""";
    }

    private String getDefaultItemsConfig() {
        return """
# Configuration of allowed items (vanilla or mmoitems IDs) used for various animal actions
items:
  tamingItem: "TAMING_ITEM"             # Item used to tame an animal.
  coownershipItem: "COOWNERSHIP_ITEM"   # Item to share ownership of an animal with another player.
  universalFeed: "UNIVERSAL_FEED"       # Item used to feed any kind of animal.
  caringItem: "CARING_ITEM"             # Item to care for or pamper an animal.
  farmReportItem: "FARMREPORT_ITEM"    # Item that shows a report or summary of the farm.
  stableChunkItem: "STABLECHUNK_ITEM"  # Item to claim a chunk as a stable (farm animals should spend the night in stable chunks).
  collectorsItem: "COLLECTOR_ITEM"     # Item used to collect rewards from animals, like wool, leather, etc.
  mountStatsItem: "CARROT_ON_A_STICK"  # Item to display mount stats or status.
  neuterItem: "SHEARS"                  # Item to neuter or sterilize animals (e.g., shears).
""";
    }

    private String getDefaultMessageConfig() {
        return """
# Messages configuration. %s will be replaced by the animal's name.
messages:
  inventoryName: "Animal Info"  # Title shown on the animal information screen.
  
  nameFirst: "§6No name found on item: pick a name using an anvil."
    # Shown when the animal item doesn't have a name yet, prompting the player to name it.

  ownerFirst: "§6This animal must be tamed before it can be co-owned."
    # Displayed when trying to share ownership but the animal is not tamed yet.

  invalidAnimalOrNotFound: "§6You cannot tame this!"
    # Message when the player attempts to tame an invalid or untamable animal.

  alreadyOwner: "§6Already an owner of %s."
    # Notifies the player they already own the specified animal (%s).

  nowCoowner: "§6Congratulations! You are now a proud co-owner of %s!"
    # Confirmation message when the player becomes a co-owner of the animal.

  ownershipShared: "§6The ownership of %s has been successfully shared."
    # Indicates that the animal's ownership has been shared with another player.

  useOnOwnedAnimal: "§6Please use this item on an animal you own."
    # Informs the player that the item they are trying to use requires ownership of the animal.

  onlyOwnerCanShare: "§6Only %s's owner can share its ownership."
    # Warns that only the main owner can share the ownership of the animal.

  nowOwner: "§6You have successfully tamed %s and are now its proud owner!"
    # Confirmation that the player tamed the animal and became its owner.

  notOwner: "§6You aren't the owner of this animal."
    # Shown when a player tries to perform an owner-only action on an animal they don't own.

  alreadyFed: "§6%s has already been fed."
    # Indicates the animal has already received food for this period.

  alreadyCared: "§6%s has already been taken care of."
    # Indicates the animal has already received care (e.g., petting) for this period.

  animalsOut: "§7§oSome animals have slept outside the farm boundaries. Make sure to secure them."
    # Warning that some animals are outside the designated farm area.

  spaceNeeded: "§7§oSome of your animals need more space. Consider expanding your farm plot."
    # Suggestion to increase farm space because animals are crowded.

  waterNeeded: "§7§oThere are animals without easy access to water."
    # Warning that some animals lack proper water supply.

  animalsFed: "§e-§oAnimals fed:§r %s."
    # Report of how many animals have been fed.

  animalsCared: "§e-§oAnimals taken care of:§r %s."
    # Report of how many animals have been cared for.

  animalsOwned: "§e-§oTotal animals owned:§r %s."
    # Displays the total count of animals owned.

  lacksSpace: "§6Make space in the inventory first!"
    # Warning that the player's inventory is full and space is needed.

  stableChunk: "§6This chunk is now a stable chunk."
    # Confirmation that a game chunk is designated as a stable (farm) area.

  alreadyStableChunk: "§6This chunk is already a stable chunk."
    # Message if trying to set a chunk as stable but it already is.

  alreadyLinked: "§6Already linked to %s."
    # Indicates that the animal or object is already linked to the specified target.

  neuteredHorse: "§6Neutered horses can't breed."
    # Info message that horses that are neutered cannot breed.
    
  tameFirst: "§6You must tame this animal before claiming it."
    # Info message that untamed animals cannot be owned.
    
  removeOwnership: "§6You are no longer the owner of %s."
    # Indicates that the animal is not longer linked to the player.
""";
    }

    private String getDefaultNumbersConfig() {
        return """
# ========== GENERAL BALANCE ==========
maxFriendshipAndGenetics: 10000   # Total points for friendship/genetics. Always shows 5 hearts (max/5 = points per heart).
initialGeneticMax: 200            # Max genetic points wild animals can start with.

# ========== INTERACTION ==========
friendshipByFeeding: 200          # Friendship gained by feeding.
friendshipByCaring: 200           # Friendship gained by caring (e.g., petting).
friendshipLostNotFeeding: 50      # Friendship lost for not feeding regularly.
friendshipLostNotCaring: 50       # Friendship lost for not caring/interacting.

# ========== ENVIRONMENT LIMITS ==========
maxAnimalsPerChunk: 20            # Max animals allowed per chunk.
maxIrlDaysOut: 3                  # Real-life days before animal loses his ownership due to not sleeping in a farm plot.
maxIrlDaysChunkAbandoned: 5       # Number of real-life days a farm chunk can stay empty before it stops being considered a farm chunk.

# ========== BREEDING & GENETICS ==========
geneticVarianceMultiplier: 0.4    # Modifies how much genetics can vary from parents. 1 = no effect.
friendshipInfluenceMultiplier: 0.4 # Scales how much parents' friendship affects baby genetics (base is 5% of average friendship).
geneticDivisorMultiplierToRetardProgression: 0.4  # Higher parent stats = slower baby improvement. Multiplies divisor (never set to 0!).

# ========== REWARDS & TIME ==========
hoursBetweenRewards: 8            # Real-life hours between reward eligibility.
lastDayChangeDate: """ + Instant.now().getEpochSecond() + " # Epoch timestamp of the last change day (UTC)." + """ 
startingDayHour: 21               # Hour of the day when the plugin triggers a day change (0–23) (UTC).
startingDayMin: 1               # Minute of the hour when the plugin triggers a day change (0–59) (UTC).

# ========== MOUNT RELATED ==========
defaultMountStats:
  minHealth: 15
  maxHealth: 30
  minSpeed: 0.1125
  maxSpeed: 0.3375
  minJump: 0.4
  maxJump: 1.0

nerfMountStats: false             # Whether to nerf mount stats automatically.
mountNerfDivisor: 2.0             # Applied if nerfMountStats is true; divides the stats.
""";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("breedingbuddies")) return null;

        if (args.length == 1) {
            // Sugerir subcomandos
            return subCommands.keySet().stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("spawnanimal")) {
            switch (args.length) {
                case 2: // EntityType
                    return Arrays.stream(EntityType.values())
                            .filter(type -> type.getEntityClass() != null && Animals.class.isAssignableFrom(type.getEntityClass()))
                            .map(Enum::name)
                            .filter(name -> name.startsWith(args[1].toUpperCase()))
                            .sorted()
                            .toList();
                case 3: // Friendship
                    return List.of("100", "1000", "10000");
                case 4: // Genetics
                    return List.of("100", "1000", "10000");
            }
        }

        if (sub.equals("spawnmount")) {
            switch (args.length) {
                case 2: // EntityType
                    return Arrays.stream(EntityType.values())
                            .filter(type -> type.getEntityClass() != null && AbstractHorse.class.isAssignableFrom(type.getEntityClass()))
                            .map(Enum::name)
                            .filter(name -> name.startsWith(args[1].toUpperCase()))
                            .sorted()
                            .toList();
                case 3: // Friendship
                    return List.of("100", "1000", "10000");
                case 4: // Health
                    return List.of("15", "30");
                case 5: // Speed
                    return List.of("0.2", "0.25", "0.3");
                case 6: // Jump
                    return List.of("0.5", "0.7", "1.0");
            }
        }

        return Collections.emptyList();
    }
}
