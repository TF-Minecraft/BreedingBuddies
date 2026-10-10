package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.events.DayChangeEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BreedingBuddiesTest extends PluginTestBase {

    private org.bukkit.command.PluginCommand command() {
        return plugin.getCommand("breedingbuddies");
    }

    private boolean run(CommandSender sender, String... args) {
        return plugin.onCommand(sender, command(), "breedingbuddies", args);
    }

    private PlayerMock admin() {
        PlayerMock admin = player();
        admin.setOp(true);
        return admin;
    }

    private Entity onlyEntityOf(Class<? extends Entity> type) {
        List<? extends Entity> found = world.getEntitiesByClass(type).stream().toList();
        assertEquals(1, found.size());
        return found.get(0);
    }

    @Test
    void enablingWritesUsableDefaultConfigs() throws Exception {
        Path folder = plugin.getDataFolder().toPath();
        for (String name : List.of("bundles.yaml", "items.yaml", "messages.yaml", "config.yaml")) {
            assertTrue(Files.exists(folder.resolve(name)), name);
        }
        assertSame(plugin, BreedingBuddies.getInstance());
        assertSame(plugin, command().getTabCompleter());
        assertEquals(plugin.getDataFolder().toPath().resolve("config.yaml").toFile(), plugin.getNumbersFile());

        assertEquals(10_000, Numbers.maxFriendshipAndGenetics);
        assertEquals(200, Numbers.initialGeneticMax);
        assertEquals(21, Numbers.startingDayHour);
        assertEquals(1, Numbers.startingDayMin);
        assertEquals(8, Numbers.hoursBetweenRewards);
        assertEquals(2.0, Numbers.mountNerfDivisor);
        assertFalse(Numbers.nerfMountStats);
        long lastChange = YamlConfiguration.loadConfiguration(plugin.getNumbersFile()).getLong("lastDayChangeDate");
        assertTrue(Math.abs(Instant.now().getEpochSecond() - lastChange) < 60);

        assertEquals("TAMING_ITEM", CustomItems.tamingItem);
        assertEquals("CARROT_ON_A_STICK", CustomItems.mountStatsItem);
        assertEquals("SHEARS", CustomItems.neuterItem);
        assertEquals("Animal Info", Messages.inventoryName);
        assertEquals("§6You are no longer the owner of %s.", Messages.removeOwnership);
        assertEquals(1.0, MountStats.getMountStats("HORSE").maxJump);
    }

    @Test
    void listenersAreRegisteredWhenEnabled() {
        PlayerMock player = player();
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                mmoItem(CustomItems.stableChunkItem), null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(click);

        assertTrue(ChunkManager.isStableChunk(player.getLocation().getChunk()));
    }

    @Test
    void savedDataIsLoadedOnEnable() throws Exception {
        UUID owner = UUID.randomUUID();
        FarmAnimal daisy = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        daisy.setState(AnimalStates.HAPPY);
        PluginData.savePlayerData(owner, java.util.Set.of(daisy));
        PluginData.getPlayerAnimals().clear();

        server.getPluginManager().disablePlugin(plugin);
        PluginData.getPlayerAnimals().clear();
        server.getPluginManager().enablePlugin(plugin);

        assertEquals("Daisy", OwnershipManager.getAnimal(daisy.getUuid(), owner).getName());
    }

    @Test
    void dataIsAutosavedAndSavedOnDisable() throws Exception {
        PlayerMock alice = player();
        own(alice, spawn(Cow.class));

        server.getScheduler().performOneTick();
        assertTrue(Files.exists(playerFile(alice.getUniqueId())));

        Files.delete(playerFile(alice.getUniqueId()));
        server.getPluginManager().disablePlugin(plugin);
        assertTrue(Files.exists(playerFile(alice.getUniqueId())));
    }

    @Test
    void commandsNeedASubcommandItRecognises() {
        PlayerMock admin = admin();

        assertFalse(run(admin));
        assertFalse(run(admin, "jump"));
        assertEquals("§cUnknown subcommand. Use /breedingbuddies <reload|spawnanimal|...>", admin.nextMessage());

        Command other = mock(Command.class);
        when(other.getName()).thenReturn("other");
        assertFalse(plugin.onCommand(admin, other, "other", new String[] {"reload"}));
    }

    @Test
    void theCommandIsReachableThroughTheServer() {
        PlayerMock admin = admin();

        assertTrue(server.dispatchCommand(admin, "breedingbuddies stablechunk"));

        assertEquals("§aStable chunk: false", admin.nextMessage());
    }

    @Test
    void reloadRereadsTheConfigFiles() throws Exception {
        PlayerMock admin = admin();
        Path config = plugin.getNumbersFile().toPath();
        Files.writeString(config, Files.readString(config).replace("maxAnimalsPerChunk: 20", "maxAnimalsPerChunk: 7"));

        assertTrue(run(admin, "reload"));

        assertEquals(7, Numbers.maxAnimalsPerChunk);
        assertEquals("§a[BreedingBuddies]§e Config reloaded successfully!", admin.nextMessage());
        assertNull(admin.nextMessage());
    }

    @Test
    void anUnwritableConfigFileFallsBackToBuiltInItemDefaults() throws Exception {
        PlayerMock admin = admin();
        Path items = plugin.getDataFolder().toPath().resolve("items.yaml");
        Files.delete(items);
        Files.createSymbolicLink(items, plugin.getDataFolder().toPath().resolve("missing").resolve("items.yaml"));

        assertTrue(run(admin, "reload"));

        assertEquals("minecraft:carrot", CustomItems.tamingItem);
        assertEquals("minecraft:paper", CustomItems.coownershipItem);
        assertEquals("minecraft:wheat", CustomItems.universalFeed);
        assertEquals("minecraft:golden_apple", CustomItems.caringItem);
        assertEquals("minecraft:book", CustomItems.farmReportItem);
        assertEquals("minecraft:fence", CustomItems.stableChunkItem);
        assertEquals("minecraft:hopper", CustomItems.collectorsItem);
        assertEquals("minecraft:carrot_on_a_stick", CustomItems.mountStatsItem);
        assertEquals("minecraft:shears", CustomItems.neuterItem);
    }

    @Test
    void stableChunkReportsWhereThePlayerStandsAndNeedsAPlayer() {
        PlayerMock admin = admin();
        ChunkManager.addChunk(admin.getLocation().getChunk());

        assertTrue(run(admin, "stablechunk"));
        assertEquals("§aStable chunk: true", admin.nextMessage());

        assertFalse(run(new ConsoleCommandSenderMock(), "stablechunk"));
    }

    @Test
    void changeDayFiresTheDayChangeEvent() {
        PlayerMock admin = admin();
        int[] fired = {0};
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(DayChangeEvent event) {
                fired[0]++;
            }
        }, plugin);

        assertTrue(run(admin, "changeday"));

        assertEquals(1, fired[0]);
        assertEquals("§a[BreedingBuddies]§e Day changed!", admin.nextMessage());
    }

    @Test
    void spawnAnimalCreatesAnUnownedSpawnedAnimal() {
        PlayerMock admin = admin();

        assertTrue(run(admin, "spawnanimal", "cow", "300", "4000"));

        Entity cow = onlyEntityOf(Cow.class);
        FarmAnimal animal = UnownedAnimalsManager.getAnimal(cow.getUniqueId());
        assertEquals(AnimalStates.SPAWNED, animal.getState());
        assertEquals(300, animal.getFriendshipPoints());
        assertEquals(4000, animal.getGeneticPoints());
        assertFalse(OwnershipManager.hasOwner(cow.getUniqueId()));
        assertEquals("§a[BreedingBuddies]§e Animal spawned successfully!", admin.nextMessage());
    }

    @Test
    void spawnAnimalRejectsBadInput() {
        PlayerMock admin = admin();
        String usage = "§cUsage: /breedingbuddies spawnanimal <entityType> <friendship> <genetics>";

        assertFalse(run(admin, "spawnanimal", "pig", "1", "1"));
        assertEquals("§cInvalid entity type specified in config.", admin.nextMessage());
        assertFalse(run(admin, "spawnanimal", "cow", "lots", "1"));
        assertEquals(usage, admin.nextMessage());
        assertFalse(run(admin, "spawnanimal", "cow"));
        assertEquals(usage, admin.nextMessage());
        ConsoleCommandSenderMock console = new ConsoleCommandSenderMock();
        assertFalse(run(console, "spawnanimal", "cow", "1", "1"));
        assertEquals(usage, console.nextMessage());
        assertTrue(world.getEntitiesByClass(Cow.class).isEmpty());
    }

    @Test
    void spawnMountCreatesAMountWithTheGivenStats() {
        PlayerMock admin = admin();

        assertTrue(run(admin, "spawnmount", "HORSE", "500", "28", "0.3", "0.9"));

        Horse horse = (Horse) onlyEntityOf(Horse.class);
        assertEquals(28, horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        assertEquals(28, horse.getHealth());
        assertEquals(0.3, horse.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue());
        assertEquals(0.9, horse.getJumpStrength(), 1e-9);
        assertEquals((byte) 1, horse.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "is_nerfed"), PersistentDataType.BYTE));
        FarmAnimal animal = UnownedAnimalsManager.getAnimal(horse.getUniqueId());
        assertEquals(AnimalStates.SPAWNED, animal.getState());
        assertEquals(500, animal.getFriendshipPoints());
        assertFalse(animal.isStableNeeded());
        assertEquals("§a[BreedingBuddies]§e Animal spawned successfully!", admin.nextMessage());
    }

    @Test
    void spawnMountRejectsBadInput() {
        PlayerMock admin = admin();
        String usage = "§cUsage: /breedingbuddies spawnmount <entityType> <friendship> <health> <speed> <jump>";
        String notMount = "§cEntity type must be a mount (like HORSE, DONKEY, etc).";

        assertFalse(run(admin, "spawnmount", "UNICORN", "1", "20", "0.2", "0.5"));
        assertEquals("§cInvalid entity type.", admin.nextMessage());
        assertFalse(run(admin, "spawnmount", "COW", "1", "20", "0.2", "0.5"));
        assertEquals(notMount, admin.nextMessage());
        assertFalse(run(admin, "spawnmount", "UNKNOWN", "1", "20", "0.2", "0.5"));
        assertEquals(notMount, admin.nextMessage());
        assertFalse(run(admin, "spawnmount", "HORSE", "1", "20.5", "0.2", "0.5"));
        assertEquals(usage, admin.nextMessage());
        assertFalse(run(admin, "spawnmount", "HORSE", "1", "20", "fast", "0.5"));
        assertEquals(usage, admin.nextMessage());
        assertFalse(run(admin, "spawnmount", "HORSE"));
        assertEquals(usage, admin.nextMessage());
        assertTrue(world.getEntitiesByClass(Horse.class).isEmpty());
    }

    @Test
    void saveAndLoadDataCommandsReachTheDisk() throws Exception {
        PlayerMock admin = admin();
        FarmAnimal daisy = own(admin, spawn(Cow.class));

        assertTrue(run(admin, "savedata"));
        assertEquals("§a[BreedingBuddies] Data saved.", admin.nextMessage());
        assertTrue(Files.exists(playerFile(admin.getUniqueId())));

        PluginData.getPlayerAnimals().clear();
        assertTrue(run(admin, "loaddata"));
        assertEquals("§a[BreedingBuddies] Data loaded.", admin.nextMessage());
        assertEquals("Daisy", OwnershipManager.getAnimal(daisy.getUuid(), admin.getUniqueId()).getName());
    }

    @Test
    void fixMakesEveryOwnedAnimalHappy() {
        PlayerMock admin = admin();
        FarmAnimal sad = own(admin, spawn(Cow.class));
        sad.setState(AnimalStates.SAD);
        FarmAnimal escaped = own(player(), spawn(Cow.class));
        escaped.setState(AnimalStates.ESCAPED);

        assertTrue(run(admin, "fix"));

        assertEquals(AnimalStates.HAPPY, sad.getState());
        assertEquals(AnimalStates.HAPPY, escaped.getState());
        assertEquals("§a[BreedingBuddies] Animals fixed.", admin.nextMessage());
    }

    @Test
    void tabCompletionSuggestsSubcommandsTypesAndValues() {
        PlayerMock admin = admin();
        Command command = command();

        assertEquals(List.of("changeday", "fix", "loaddata", "reload", "savedata", "spawnanimal", "spawnmount", "stablechunk"),
                plugin.onTabComplete(admin, command, "bb", new String[] {""}));
        assertEquals(List.of("spawnanimal", "spawnmount"), plugin.onTabComplete(admin, command, "bb", new String[] {"SP"}));

        List<String> animals = plugin.onTabComplete(admin, command, "bb", new String[] {"spawnanimal", "c"});
        assertTrue(animals.containsAll(List.of("CAMEL", "CAT", "CHICKEN", "COW")));
        assertFalse(animals.contains("COD"), "fish are not animals");
        assertFalse(animals.contains("CREEPER"));
        assertEquals(List.of("100", "1000", "10000"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnanimal", "COW", ""}));
        assertEquals(List.of("100", "1000", "10000"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnanimal", "COW", "1", ""}));
        assertEquals(List.of(), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnanimal", "COW", "1", "1", ""}));

        assertEquals(List.of("HORSE"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "ho"}));
        assertEquals(List.of("100", "1000", "10000"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "HORSE", ""}));
        assertEquals(List.of("15", "30"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "HORSE", "1", ""}));
        assertEquals(List.of("0.2", "0.25", "0.3"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "HORSE", "1", "1", ""}));
        assertEquals(List.of("0.5", "0.7", "1.0"), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "HORSE", "1", "1", "1", ""}));
        assertEquals(List.of(), plugin.onTabComplete(admin, command, "bb", new String[] {"spawnmount", "HORSE", "1", "1", "1", "1", ""}));

        assertEquals(List.of(), plugin.onTabComplete(admin, command, "bb", new String[] {"reload", ""}));
        Command other = mock(Command.class);
        when(other.getName()).thenReturn("other");
        assertNull(plugin.onTabComplete(admin, other, "other", new String[] {""}));
    }
}
