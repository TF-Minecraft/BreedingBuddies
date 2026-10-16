package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmocore.manager.profession.ProfessionManager;
import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRuntimeCoverageTest.Farm;
import net.tfminecraft.cooking.api.CookingFoodApi;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.database.SqliteDatabase;
import net.tfminecraft.tlibs.database.SqliteDatabaseException;

class HusbandryRemainingCoverageTest {
    @TempDir Path directory;
    Farm f;
    @BeforeEach void open() throws Exception { f = new Farm(directory); HusbandryFeedQuality.clear(); }
    @AfterEach void close() throws Exception { HusbandryFeedQuality.clear(); f.close(); }
    PlayerInteractEntityEvent interaction(Entity entity) { return new PlayerInteractEntityEvent(f.player, entity, EquipmentSlot.HAND); }
    void hold(Material material, int amount) { f.player.getInventory().setItemInMainHand(new ItemStack(material, amount)); }
    int held() { return f.player.getInventory().getItemInMainHand().getAmount(); }
    void clearMessages() { while (f.player.nextMessage() != null) { } }
    void products() throws Exception {
        for (String species : List.of("COW", "SHEEP")) {
            f.config.set("species." + species + ".slaughter.meat", "meat(type=roast)");
            for (String mode : List.of("slaughter", "shear", "shed")) {
                f.config.set("species." + species + "." + mode + ".drops.mode", "counted");
                f.config.set("species." + species + "." + mode + ".drops.common", List.of(Map.of("path", "wool", "amount", 2, "weight", 1)));
            }
        }
        f.config.set("species.CHICKEN.egg", "vanilla"); f.reload();
        when(TLibs.getItemAPI().getCreator().getItemFromPath("wool")).thenAnswer(inv -> new ItemStack(Material.WHITE_WOOL));
    }

    @Test void careFeedsOnlyHungryAnimalsAndCleansWithoutConsumingTheGlove() {
        var listener = new HusbandryCareListener(); Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        listener.onInteract(new PlayerInteractEntityEvent(f.player, cow, EquipmentSlot.OFF_HAND)); listener.onInteract(interaction(f.spawn(ItemDisplay.class)));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); listener.onInteract(interaction(cow)); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        listener.onInteract(interaction(f.spawn(Cow.class))); hold(Material.STONE, 3); var ignored = interaction(cow); listener.onInteract(ignored); assertFalse(ignored.isCancelled());
        hold(Material.WHEAT, 3); listener.onInteract(interaction(cow)); assertEquals(3, held());
        animal.setHungrySince(1L); var feed = interaction(cow); listener.onInteract(feed); assertTrue(feed.isCancelled()); assertEquals(2, held()); assertNull(animal.hungrySince()); assertNull(f.repository.getAnimal(animal.uuid()).orElseThrow().hungrySince());
        hold(Material.LEATHER, 1); listener.onInteract(interaction(cow)); assertEquals(1, held());
        animal.setDirtySince(1L); var clean = interaction(cow); listener.onInteract(clean); assertNull(animal.dirtySince()); assertEquals(1, held()); assertTrue(clean.isCancelled());
        assertNull(f.repository.getAnimal(animal.uuid()).orElseThrow().dirtySince());
        animal.setHungrySince(1L); f.repository.deleteCharacterOwner(animal.uuid(), f.player.getUniqueId()); hold(Material.WHEAT, 2); listener.onInteract(interaction(cow)); assertEquals(2, held()); assertNotNull(animal.hungrySince());
        f.player.setOp(true); listener.onInteract(interaction(cow)); assertEquals(1, held()); assertNull(animal.hungrySince());
    }

    @Test void neuteringRequiresTheToolARecordAndOwnershipAndOnlyUsesTheToolOnce() {
        var listener = new HusbandryNeuterListener(); Cow cow = f.spawn(Cow.class);
        listener.onInteract(new PlayerInteractEntityEvent(f.player, cow, EquipmentSlot.OFF_HAND)); listener.onInteract(interaction(cow));
        hold(Material.SHEARS, 1); listener.onInteract(interaction(f.spawn(ItemDisplay.class)));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); listener.onInteract(interaction(cow)); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        listener.onInteract(interaction(cow)); assertTrue(f.player.nextMessage().contains("cannot be neutered"));
        HusbandryAnimal animal = f.record(cow); listener.onInteract(interaction(cow)); assertTrue(f.player.nextMessage().contains("not your")); assertFalse(animal.neutered());
        f.own(animal, f.player); var event = interaction(cow); listener.onInteract(event); assertTrue(event.isCancelled()); assertTrue(animal.neutered());
        assertTrue(f.repository.getAnimal(animal.uuid()).orElseThrow().neutered()); assertEquals(1, ((Damageable) f.player.getInventory().getItemInMainHand().getItemMeta()).getDamage());
        listener.onInteract(interaction(cow)); assertEquals(1, ((Damageable) f.player.getInventory().getItemInMainHand().getItemMeta()).getDamage());
        Cow other = f.spawn(Cow.class); HusbandryAnimal second = f.record(other); f.player.setOp(true); listener.onInteract(interaction(other)); assertTrue(second.neutered());
    }

    @Test void damageRulesDistinguishOwnersStaffStrangersMobsProjectilesAndEnvironment() throws Exception {
        var listener = new HusbandryDamageListener(); Cow cow = f.spawn(Cow.class);
        var unmanaged = new EntityDamageEvent(cow, EntityDamageEvent.DamageCause.FALL, 2); listener.onDamage(unmanaged); assertFalse(unmanaged.isCancelled());
        HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        for (String key : List.of("owner", "other-players", "mobs", "environment")) f.config.set("damage." + key, false); f.reload();
        assertTrue(attack(listener, cow, f.player)); assertTrue(attack(listener, cow, f.neighbour)); assertTrue(attack(listener, cow, f.spawn(Cow.class)));
        f.neighbour.setOp(true); assertFalse(attack(listener, cow, f.neighbour));
        Arrow arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(f.player); assertTrue(attack(listener, cow, arrow)); when(arrow.getShooter()).thenReturn(null); assertFalse(attack(listener, cow, arrow));
        assertFalse(attack(listener, cow, f.spawn(ItemDisplay.class)));
        var fall = new EntityDamageEvent(cow, EntityDamageEvent.DamageCause.FALL, 2); listener.onDamage(fall); assertTrue(fall.isCancelled());
        var custom = new EntityDamageEvent(cow, EntityDamageEvent.DamageCause.CUSTOM, 2); listener.onDamage(custom); assertFalse(custom.isCancelled());
        for (String key : List.of("owner", "other-players", "mobs", "environment")) f.config.set("damage." + key, true); f.reload();
        f.neighbour.setOp(false); assertFalse(attack(listener, cow, f.player)); assertFalse(attack(listener, cow, f.neighbour)); assertFalse(attack(listener, cow, f.spawn(Cow.class)));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); assertFalse(attack(listener, cow, f.player));
    }
    boolean attack(HusbandryDamageListener listener, Entity victim, Entity attacker) {
        var event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2); listener.onDamage(event); return event.isCancelled();
    }

    @Test void deathCleansRecordsReplacesMatureRoastsAndPreservesVanillaWool() throws Exception {
        products(); var listener = new HusbandryDeathListener(); Cow absent = f.spawn(Cow.class); var untouched = death(absent); listener.onDeath(untouched); assertEquals(2, untouched.getDrops().size());
        when(f.plugin.getHusbandryRepository()).thenReturn(null); listener.onDeath(untouched); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        try (var harvest = mockStatic(HusbandryHarvest.class, CALLS_REAL_METHODS)) {
            harvest.when(() -> HusbandryHarvest.buildFood(any(), anyString())).thenAnswer(inv -> new ItemStack(Material.COOKED_BEEF));
            Cow mature = f.spawn(Cow.class); HusbandryAnimal adult = f.record(mature); f.own(adult, f.player); var event = death(mature); listener.onDeath(event);
            assertTrue(event.getDrops().stream().anyMatch(item -> item.getType() == Material.COOKED_BEEF)); assertTrue(event.getDrops().stream().anyMatch(item -> item.getType() == Material.WHITE_WOOL));
            assertFalse(event.getDrops().stream().anyMatch(item -> item.getType() == Material.BEEF)); assertFalse(f.repository.exists(adult.uuid())); assertTrue(HusbandryEntities.getLoaded(adult.uuid()).isEmpty());
            Cow young = f.spawn(Cow.class); HusbandryAnimal baby = f.record(young); baby.setMatureAt(System.currentTimeMillis() + 60_000); var babyDrops = death(young); listener.onDeath(babyDrops); assertEquals(Material.BEEF, babyDrops.getDrops().getFirst().getType());
            for (ItemStack invalid : Arrays.asList(null, new ItemStack(Material.AIR))) {
                harvest.when(() -> HusbandryHarvest.buildFood(any(), anyString())).thenReturn(invalid); Cow failed = f.spawn(Cow.class); f.record(failed); var failedOutput = death(failed); listener.onDeath(failedOutput);
                assertTrue(failedOutput.getDrops().stream().anyMatch(item -> item.getType() == Material.BEEF), "Failed custom roast creation must preserve vanilla meat");
                assertTrue(failedOutput.getDrops().stream().noneMatch(item -> item.getType().isAir()));
            }
        }
        f.config.set("remove-unowned", List.of("COW")); f.reload(); Cow wild = f.spawn(Cow.class); HusbandryAnimal record = f.record(wild); var wiped = death(wild); listener.onDeath(wiped);
        assertTrue(wiped.getDrops().isEmpty()); assertEquals(0, wiped.getDroppedExp()); assertFalse(f.repository.exists(record.uuid()));
    }
    EntityDeathEvent death(LivingEntity entity) { return new EntityDeathEvent(entity, mock(org.bukkit.damage.DamageSource.class), new ArrayList<>(List.of(new ItemStack(Material.BEEF), new ItemStack(Material.WHITE_WOOL))), 5); }

    @Test void animalCommandsReportAvailabilityPermissionsAndCurrentOwnedLocations() {
        var command = new HusbandryAnimalsCommand(); CommandSender console = mock(CommandSender.class);
        BreedingBuddies.plugin = null; assertTrue(command.onCommand(f.player, null, "animals", new String[0])); assertTrue(f.player.nextMessage().contains("not available")); BreedingBuddies.plugin = f.plugin;
        command.onCommand(f.player, null, "animals", new String[]{"Neighbour"}); assertTrue(f.player.nextMessage().contains("only list your own"));
        command.onCommand(console, null, "animals", new String[0]); verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));
        f.player.setOp(true); command.onCommand(f.player, null, "animals", new String[]{"NeverJoined"}); assertTrue(f.player.nextMessage().contains("No player"));
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player); cow.teleport(new Location(f.world, 9, 66, 8)); animal.setDirtySince(1L);
        command.onCommand(f.player, null, "animals", new String[0]); assertEquals(9, animal.x()); assertEquals("Your character's animals (1)", ChatColor.stripColor(f.player.nextMessage())); assertEquals("Account animal places: 1/2", ChatColor.stripColor(f.player.nextMessage())); clearMessages();
        HusbandryAnimal offline = f.ownedOffline("Offline"); command.onCommand(f.player, null, "animals", new String[]{f.player.getName()}); assertNotNull(f.player.nextMessage()); clearMessages();
        assertEquals(List.of("Farmer"), command.onTabComplete(f.player, null, "animals", new String[]{"f"})); assertEquals(List.of("Farmer", "Neighbour"), command.onTabComplete(f.player, null, "animals", new String[]{""}));
        assertTrue(command.onTabComplete(f.neighbour, null, "animals", new String[]{""}).isEmpty()); assertTrue(command.onTabComplete(f.player, null, "animals", new String[0]).isEmpty());
        assertTrue(f.repository.exists(offline.uuid()));
    }

    @Test void itemUsesHandleCreativeDurabilityBreakingStacksAndStoredLabels() {
        assertFalse(HusbandryItems.matches(null, "feed")); assertFalse(HusbandryItems.matches(new ItemStack(Material.WHEAT), " ")); assertFalse(HusbandryItems.matches(new ItemStack(Material.WHEAT), null));
        HusbandryItems.useFromMainHand(null); HusbandryItems.consumeOne(null); ItemStack empty = new ItemStack(Material.AIR); HusbandryItems.consumeOne(empty); assertSame(empty, HusbandryItems.applyUse(empty)); assertNull(HusbandryItems.applyUse(null));
        hold(Material.SHEARS, 1); var tool = f.player.getInventory().getItemInMainHand(); var meta = (Damageable) tool.getItemMeta(); meta.setDamage(Material.SHEARS.getMaxDurability() - 1); tool.setItemMeta(meta); HusbandryItems.useFromMainHand(f.player); assertTrue(f.player.getInventory().getItemInMainHand().getType().isAir());
        hold(Material.WHEAT, 3); f.player.setGameMode(GameMode.CREATIVE); HusbandryItems.useFromMainHand(f.player); assertEquals(3, held()); f.player.setGameMode(GameMode.SPECTATOR); HusbandryItems.useFromMainHand(f.player); assertEquals(3, held());
        f.player.setGameMode(GameMode.SURVIVAL); HusbandryItems.useFromMainHand(f.player); assertEquals(2, held());
        ItemStack label = new ItemStack(Material.NAME_TAG); HusbandryItems.setTameName(label, " ??? "); assertNull(HusbandryItems.tameName(label)); HusbandryItems.setTameName(label, "  Hazel "); assertEquals("Hazel", HusbandryItems.tameName(label));
        assertNull(HusbandryItems.applyUse(new ItemStack(Material.WHEAT)), "Using the last consumable must empty the hand");
        HusbandryItems.setTameName(null, "Hazel"); HusbandryItems.setTameName(label, null); HusbandryItems.setTameName(label, " "); HusbandryItems.setTameName(empty, "Hazel");
        assertNull(HusbandryItems.tameName(null)); assertNull(HusbandryItems.linkedAnimal(null)); assertNull(HusbandryItems.linkedAnimal(empty));
        HusbandryItems.setLinkedAnimal(null, "id", ""); HusbandryItems.setLinkedAnimal(label, null, ""); HusbandryItems.setLinkedAnimal(empty, "id", "");
        HusbandryItems.setLinkedAnimal(label, "first", " "); assertEquals("first", HusbandryItems.linkedAnimal(label)); HusbandryItems.setLinkedAnimal(label, "second", "Animal name"); assertEquals(List.of("Animal name"), label.getItemMeta().getLore());
    }

    @Test void weightedDropTablesRespectCountersMissingSpeciesMissesAndUnavailableTemplates() throws Exception {
        products(); Random random = new Random(7); HusbandryAnimal animal = f.record(f.spawn(Cow.class)); animal.setCare(HusbandryConfig.careMax());
        assertFalse(HusbandryDropRoller.rollSlaughterExtras(animal, random, 0).isEmpty()); assertFalse(HusbandryDropRoller.rollShearDrops(animal, random, 0).isEmpty()); assertFalse(HusbandryDropRoller.rollShedDrops(animal, random).isEmpty());
        f.config.set("species.COW.shed.drops.mode", "single"); f.reload(); assertEquals(1, HusbandryDropRoller.rollShedDrops(animal, random).size());
        for (String type : List.of("PIG", "not-an-entity")) {
            HusbandryAnimal missing = new HusbandryAnimal(UUID.randomUUID(), type, "Missing"); assertTrue(HusbandryDropRoller.rollSlaughterExtras(missing, random, 0).isEmpty()); assertTrue(HusbandryDropRoller.rollShearDrops(missing, random, 0).isEmpty()); assertTrue(HusbandryDropRoller.rollShedDrops(missing, random).isEmpty());
        }
        assertTrue(HusbandryDropRoller.rollSlaughterExtras(null, random, 0).isEmpty()); assertTrue(HusbandryDropRoller.rollShearDrops(null, random, 0).isEmpty()); assertTrue(HusbandryDropRoller.rollShedDrops(null, random).isEmpty());
        var present = new HusbandryDropTable(List.of(new HusbandryDropEntry("wool", 2, 1)), null, null, null); assertEquals(2, HusbandryDropRoller.rollExtras(present, animal, random).orElseThrow().getAmount());
        var counted = new HusbandryDropTable(present.common(), null, null, null, true); assertEquals(1, HusbandryDropRoller.roll(counted, animal, random, 0, HusbandryDropRoller.CountMode.SINGLE).size());
        var miss = new HusbandryDropTable(List.of(new HusbandryDropEntry("", 1, 1)), null, null, null, true); assertTrue(HusbandryDropRoller.roll(miss, animal, random, 0, HusbandryDropRoller.CountMode.SINGLE).isEmpty()); assertTrue(HusbandryDropRoller.rollExtras(miss, animal, random).isEmpty());
        var locked = new HusbandryDropTable(List.of(), null, null, List.of(new HusbandryDropEntry("wool", 1, 1)), true); assertTrue(HusbandryDropRoller.roll(locked, animal, random, 0, HusbandryDropRoller.CountMode.SINGLE).isEmpty()); assertTrue(HusbandryDropRoller.rollExtras(locked, animal, random).isEmpty());
        assertTrue(HusbandryDropRoller.rollExtras(null, animal, random).isEmpty()); assertTrue(HusbandryDropRoller.rollExtras(present, null, random).isEmpty()); assertTrue(HusbandryDropRoller.rollExtras(present, animal, null).isEmpty()); assertTrue(HusbandryDropRoller.unlockedPool(null, 5).isEmpty());
        assertEquals(0, HusbandryDropRoller.hideCount(null, 0)); assertFalse(HusbandryDropRoller.woolBlocked(null, animal, 0)); assertNull(HusbandryDropRoller.pickEntry(null, random));
        when(TLibs.getItemAPI().getCreator().getItemFromPath("wool")).thenReturn(null); assertTrue(HusbandryDropRoller.roll(counted, animal, random, 0, HusbandryDropRoller.CountMode.SINGLE).isEmpty());
        HusbandryAnimal sheep = f.record(f.spawn(Sheep.class)); sheep.setWoolReadyAt(100L); assertTrue(HusbandryDropRoller.rollSlaughterExtras(sheep, random, 0).isEmpty());
    }

    @Test void largePositiveDropWeightsStillSelectAReward() {
        var first = new HusbandryDropEntry("first", 1, Integer.MAX_VALUE); var second = new HusbandryDropEntry("second", 1, Integer.MAX_VALUE);
        assertNotNull(HusbandryDropRoller.pickEntry(List.of(first, second), new Random(7)), "Positive weights must not overflow into an empty reward pool");
    }

    @Test void sheepHarvestReadinessTimersAndOutputCreationHandleSupportedAndMissingValues() throws Exception {
        products(); HusbandryAnimal sheep = f.record(f.spawn(Sheep.class)); HusbandrySpecies species = HusbandryConfig.species(EntityType.SHEEP);
        HusbandryHarvest.prepareNewAnimal(null, null); HusbandryHarvest.prepareNewAnimal(sheep, EntityType.SHEEP); assertNotNull(sheep.woolReadyAt()); assertNotNull(sheep.shedReadyAt());
        HusbandryAnimal chicken = f.record(f.spawn(Chicken.class)); HusbandryHarvest.prepareNewAnimal(chicken, EntityType.CHICKEN); assertNotNull(chicken.eggReadyAt());
        assertEquals(1, HusbandryHarvest.stars(null)); assertEquals(HusbandryConfig.starsForGenetics(sheep.genetics()), HusbandryHarvest.stars(sheep));
        assertEquals(HusbandryHarvest.ShearResult.COOLDOWN, HusbandryHarvest.shearReadiness(null, species, 100));
        sheep.setMatureAt(200L); assertEquals(HusbandryHarvest.ShearResult.IMMATURE, HusbandryHarvest.tryShear(f.player, null, sheep, species, f.repository, 100));
        sheep.setMatureAt(null); sheep.setWoolReadyAt(200L); assertEquals(HusbandryHarvest.ShearResult.COOLDOWN, HusbandryHarvest.tryShear(f.player, null, sheep, species, f.repository, 100));
        sheep.setWoolReadyAt(null); assertEquals(HusbandryHarvest.ShearResult.DONE, HusbandryHarvest.tryShear(null, null, sheep, species, null, 100)); assertTrue(sheep.woolReadyAt() > 100);
        for (String path : new String[] {null, "", "vanilla"}) assertNull(HusbandryHarvest.buildTlibs(path, 2));
        assertEquals(64, HusbandryHarvest.buildTlibs("wool", 500).getAmount()); assertEquals(1, HusbandryHarvest.buildTlibs("wool", 0).getAmount()); assertFalse(HusbandryHarvest.isWoolDrop(null)); assertTrue(HusbandryHarvest.isWoolDrop(new ItemStack(Material.RED_WOOL)));
        when(TLibs.getItemAPI().getCreator().getItemFromPath("missing")).thenReturn(null); assertNull(HusbandryHarvest.buildTlibs("missing", 1));
        when(TLibs.getItemAPI().getCreator().getItemFromPath("bad")).thenThrow(new IllegalArgumentException("Bad external template")); assertNull(HusbandryHarvest.buildTlibs("bad", 1));
    }

    @Test void professionExperienceHonorsAvailabilityConfiguredAmountsAndExternalFailures() throws Exception {
        HusbandryProfessionXp.tryGive(null, EntityType.COW); HusbandryProfessionXp.tryGive(f.player, EntityType.COW);
        MockBukkit.createMockPlugin("MMOCore"); HusbandryConfig.setProfessionExp("", HusbandryExpBracket.of(4, 4)); HusbandryProfessionXp.tryGive(f.player, EntityType.COW);
        HusbandryConfig.setProfessionExp("farming", HusbandryExpBracket.of(0, 0)); HusbandryProfessionXp.tryGive(f.player, EntityType.COW);
        MMOCore previous = MMOCore.plugin; MMOCore api = mock(MMOCore.class); ProfessionManager professions = mock(ProfessionManager.class);
        // MMOCore exposes this final collaborator as a public field; adapt that external plugin boundary only.
        var managerField = MMOCore.class.getField("professionManager"); managerField.setAccessible(true); managerField.set(api, professions); MMOCore.plugin = api;
        try (MockedStatic<PlayerData> players = mockStatic(PlayerData.class)) {
            PlayerData data = mock(PlayerData.class, RETURNS_DEEP_STUBS); players.when(() -> PlayerData.get(f.player)).thenReturn(data);
            HusbandryProfessionGrant.give(null, "farming", 3); HusbandryProfessionGrant.give(f.player, null, 3); HusbandryProfessionGrant.give(f.player, " ", 3); HusbandryProfessionGrant.give(f.player, "farming", 0);
            HusbandryProfessionGrant.give(f.player, "absent", 3); verify(data, never()).getCollectionSkills();
            Profession profession = mock(Profession.class); when(professions.get("farming")).thenReturn(profession); HusbandryConfig.setProfessionExp("farming", HusbandryExpBracket.of(4, 4));
            HusbandryProfessionXp.tryGive(f.player, EntityType.COW); verify(data.getCollectionSkills()).giveExperience(profession, 4, EXPSource.SOURCE);
            var collections = data.getCollectionSkills(); doThrow(new IllegalStateException("MMOCore unavailable during reload")).when(collections).giveExperience(profession, 4, EXPSource.SOURCE);
            assertDoesNotThrow(() -> HusbandryProfessionXp.tryGive(f.player, EntityType.COW));
        } finally { MMOCore.plugin = previous; }
    }

    @Test void publicModelsNormalizeMissingValuesAndGrowthReleasesAgeLocks() throws Exception {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Cow"); UUID replacement = UUID.randomUUID(); animal.setUuid(replacement); assertEquals(replacement, animal.uuid());
        animal.setLastLocation("farm", 1, 2, 3); animal.setLastLocation(" ", 4, 5, 6); assertFalse(animal.hasLocation());
        assertEquals("owner", new HusbandryOwned(animal, " ").role()); assertEquals(HusbandryAnimalState.UNTAMED, HusbandryAnimalState.fromStorage(null)); assertEquals(HusbandryAnimalState.UNTAMED, HusbandryAnimalState.fromStorage("unknown"));
        assertEquals("Big Cow", HusbandryRoster.speciesLabel("BIG__COW"));
        var bands = List.of(new HusbandryQualityBand(0, 1), new HusbandryQualityBand(500, 3), new HusbandryQualityBand(1000, 5));
        var bounds = HusbandryQualityRange.of(500, 200, 100, bands); assertEquals(new HusbandryQualityRange.Bounds(3, 5), bounds);
        assertEquals(1, HusbandryQualityRange.roll(null, null)); assertTrue(HusbandryQualityRange.roll(bounds, null) >= bounds.min());
        Cow cow = f.spawn(Cow.class); cow.setBaby(); cow.setAgeLock(true); HusbandryGrowth.applyMaturity(cow, animal, 100); assertTrue(cow.isAdult()); assertFalse(cow.getAgeLock());
        HusbandryGrowth.applyMaturity(null, animal, 100); HusbandryGrowth.applyMaturity(cow, null, 100);
        Villager villager = mock(Villager.class); when(villager.isAdult()).thenReturn(false); HusbandryGrowth.applyMaturity(villager, animal, 100); verify(villager).setAdult();
        Ageable ageable = mock(Ageable.class); when(ageable.isAdult()).thenReturn(false); HusbandryGrowth.applyMaturity(ageable, animal, 100); verify(ageable).setAdult();
        LivingEntity nonAgeable = mock(LivingEntity.class); HusbandryGrowth.applyMaturity(nonAgeable, animal, 100); verifyNoInteractions(nonAgeable);
        animal.setHungrySince(1L); var roster = HusbandryRoster.render("Farmer", true, List.of(new HusbandryOwned(animal, "owner")), 2, 100);
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(roster.getLast()).contains("Hungry"));
        animal.setMatureAt(200L); cow.setAdult(); cow.setAgeLock(false); HusbandryGrowth.applyMaturity(cow, animal, 100); assertFalse(cow.isAdult()); assertTrue(cow.getAgeLock());
        assertTrue(HusbandryEntities.getLoaded(null).isEmpty());
        products(); assertTrue(HusbandryProducts.modeLines(HusbandryConfig.species(EntityType.COW)).contains("Shed")); assertTrue(HusbandryProducts.modeLines(null).isEmpty());
        f.config.set("remove-unowned", List.of("COW")); f.reload(); HusbandryClaimHint.remind(f.player, cow); clearMessages(); HusbandryClaimHint.remind(f.player, cow); assertNull(f.player.nextMessage());
    }

    @Test void feedingQualityExpiresOffersAndFallsBackWhenExternalMatchingFails() {
        assertEquals(1, HusbandryFeedQuality.clampScale(Double.NaN)); assertEquals(0.5, HusbandryFeedQuality.combine(null, 0.5));
        HusbandryFeedQuality.offer(null, 0.5, 0); HusbandryFeedQuality.commitOffer(null, 0); HusbandryFeedQuality.remember(null, 0.5, 0);
        UUID id = UUID.randomUUID(); HusbandryFeedQuality.offer(id, 0.4, 0); HusbandryFeedQuality.commitOffer(id, 3000); assertEquals(1, HusbandryFeedQuality.takePair(id, null, 3000));
        HusbandryFeedQuality.remember(id, 0.2, 0); assertEquals(1, HusbandryFeedQuality.takePair(id, null, 46_000));
        HusbandryFeedQuality.offer(id, 0.5, 0); HusbandryFeedQuality.discardOffer(id); HusbandryFeedQuality.commitOffer(id, 1); assertEquals(1, HusbandryFeedQuality.consumeBreedingScale(id, null, new ItemStack(Material.STONE), 1));
        UUID father = UUID.randomUUID(); HusbandryFeedQuality.remember(id, 0.4, 0); HusbandryFeedQuality.remember(father, 0.8, 0); assertEquals(0.6, HusbandryFeedQuality.consumeBreedingScale(id, father, null, 1), 0.0001);
        assertEquals(1, HusbandryFeedQuality.scaleOf(null)); assertEquals(1, HusbandryFeedQuality.scaleOf(new ItemStack(Material.AIR)));
        ItemStack quality = new ItemStack(Material.CARROT); var meta = quality.getItemMeta(); meta.getPersistentDataContainer().set(CookingApiStub.QUALITY, PersistentDataType.INTEGER, 2); quality.setItemMeta(meta);
        when(TLibs.getItemAPI().getChecker().checkItemWithPath(quality, "feed")).thenThrow(new IllegalArgumentException("External matcher unavailable")); assertEquals(0.4, HusbandryFeedQuality.scaleOf(quality));
        ItemStack noMeta = spy(new ItemStack(Material.CARROT)); doReturn(false).when(noMeta).hasItemMeta(); assertEquals(1, HusbandryFeedQuality.scaleOf(noMeta));
        ItemStack absentMeta = mock(ItemStack.class); when(absentMeta.getType()).thenReturn(Material.CARROT); when(absentMeta.hasItemMeta()).thenReturn(true); assertEquals(1, HusbandryFeedQuality.scaleOf(absentMeta));
    }

    @Test void spawningClampsStatsPersistsTheRecordAndRemovesAnUnrecordableSpawn() {
        assertNull(HusbandrySpawner.spawn(null, EntityType.COW, 1, 1)); assertNull(HusbandrySpawner.spawn(f.player, null, 1, 1)); assertNull(HusbandrySpawner.spawn(f.player, EntityType.UNKNOWN, 1, 1)); assertNull(HusbandrySpawner.spawn(f.player, EntityType.ITEM_DISPLAY, 1, 1));
        assertNull(HusbandrySpawner.createRecord(null, 1, 1)); Cow cow = f.spawn(Cow.class); Player player = mock(Player.class); World world = mock(World.class);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(new Location(world, 1, 65, 1)); when(world.spawnEntity(any(Location.class), eq(EntityType.COW))).thenReturn(cow);
        assertSame(cow, HusbandrySpawner.spawn(player, EntityType.COW, Integer.MAX_VALUE, Integer.MAX_VALUE));
        HusbandryAnimal saved = f.repository.getAnimal(cow.getUniqueId()).orElseThrow(); assertEquals(HusbandryConfig.maxGenetics(), saved.genetics()); assertEquals(HusbandryConfig.careMax(), saved.care()); assertEquals(saved.name(), cow.getCustomName()); assertFalse(cow.isCustomNameVisible());
        when(f.plugin.getHusbandryRepository()).thenReturn(null); assertNull(HusbandrySpawner.spawn(player, EntityType.COW, 1, 1)); assertFalse(cow.isValid()); assertNull(HusbandrySpawner.createRecord(cow, 1, 1));
    }

    @Test void harvestFoodAsksCookingForTheConfiguredRoastPortion() {
        HusbandryAnimal animal = f.record(f.spawn(Cow.class)); animal.setGenetics(HusbandryConfig.maxGenetics()); animal.setCare(HusbandryConfig.careMax());
        assertNull(HusbandryHarvest.buildFood(null, "roast")); f.cooking.api.verifyNoInteractions();
        int cuts = HusbandryConfig.roastCutsFor(HusbandryConfig.effectiveGenetics(animal)); ItemStack roast = new ItemStack(Material.COOKED_BEEF);
        f.cooking.api.when(() -> CookingFoodApi.buildRoast(eq("roast"), anyInt(), eq(cuts))).thenReturn(roast);
        assertSame(roast, HusbandryHarvest.buildFood(animal, "roast"));
        f.cooking.api.verify(() -> CookingFoodApi.buildRoast(eq("roast"), intThat(stars -> stars >= 1 && stars <= 5), eq(cuts)));
        assertNull(HusbandryHarvest.buildMilk(null, EntityType.GOAT)); assertEquals(Material.MILK_BUCKET, HusbandryHarvest.buildMilk(animal, EntityType.GOAT).getType());
        f.cooking.api.verify(() -> CookingFoodApi.buildMilk(eq(EntityType.GOAT), intThat(stars -> stars >= 1 && stars <= 5)));
    }

    @Test void shearingDropsRewardsWhenThePlayersInventoryIsFull() throws Exception {
        products(); Sheep sheep = f.spawn(Sheep.class); HusbandryAnimal animal = f.record(sheep);
        // MockBukkit's generic addItem scans every backing slot, including equipment/reserved slots.
        for (int slot = 0; slot < f.player.getInventory().getSize(); slot++) f.player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        f.player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
        f.player.getInventory().setItemInOffHand(new ItemStack(Material.STONE, 64));
        assertEquals(-1, f.player.getInventory().firstEmpty(), () -> Arrays.toString(f.player.getInventory().getContents()));
        assertEquals(HusbandryHarvest.ShearResult.DONE, HusbandryHarvest.tryShear(f.player, sheep, animal, HusbandryConfig.species(EntityType.SHEEP), f.repository, 100));
        int droppedWool = f.world.getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast).map(Item::getItemStack).filter(stack -> stack.getType() == Material.WHITE_WOOL).mapToInt(ItemStack::getAmount).sum();
        assertEquals(2 * HusbandryConfig.woolFor(HusbandryConfig.effectiveGenetics(animal)), droppedWool, () -> "A full inventory must drop all earned wool instead of discarding it: " + Arrays.toString(f.player.getInventory().getContents()));
        assertEquals(100 + HusbandryConfig.woolTimerSeconds(EntityType.SHEEP) * 1000L, f.repository.getAnimal(animal.uuid()).orElseThrow().woolReadyAt());
    }

    @Test void anAirExternalHarvestItemIsNotAReward() {
        when(TLibs.getItemAPI().getCreator().getItemFromPath("air")).thenReturn(new ItemStack(Material.AIR)); assertNull(HusbandryHarvest.buildTlibs("air", 1));
    }

    @Test void repositoryRejectsIncompleteInputsAndPreservesValidRows() {
        HusbandryRepository repository = f.repository; HusbandryAnimal good = f.offline("COW", "???"), invalid = new HusbandryAnimal(null, "COW", "No ID");
        repository.upsertAnimal(null); repository.upsertAnimal(invalid); repository.upsertAnimals(null); repository.upsertAnimals(List.of()); repository.upsertAnimals(Arrays.asList(null, invalid)); repository.upsertAnimals(Arrays.asList(null, good));
        assertEquals("", repository.getAnimal(good.uuid()).orElseThrow().name()); assertTrue(repository.getAnimal(null).isEmpty()); assertFalse(repository.exists(null)); repository.deleteAnimal(null);
        repository.upsertOwner(null); repository.upsertOwner(new HusbandryOwner(null, null, null, null)); repository.deleteCharacterOwner(null, null); assertEquals(0, repository.countForPlayer(null)); assertTrue(repository.listOwners(null).isEmpty()); assertTrue(repository.listForPlayer(null).isEmpty());
        repository.upsertSnapshots(null, 0); repository.upsertSnapshots(Map.of(), 0); Map<UUID, byte[]> snapshots = new LinkedHashMap<>(); snapshots.put(null, new byte[]{1}); snapshots.put(good.uuid(), null); repository.upsertSnapshots(snapshots, 1);
        assertTrue(repository.getSnapshot(good.uuid()).isEmpty()); assertTrue(repository.getSnapshot(null).isEmpty()); repository.deleteSnapshot(null); assertEquals(0, repository.resetStaleStats(" ", null));
    }

    @Test void legacySchemaMigrationAddsEveryGenerationOfFieldsWithoutLosingAnimals() throws Exception {
        File file = directory.resolve("legacy.db").toFile(); HusbandryRepository created = HusbandryRepository.open(file); UUID id = UUID.randomUUID(); created.upsertAnimal(new HusbandryAnimal(id, "COW", "Legacy")); created.close();
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            for (String column : List.of("mature_at", "shed_ready_at", "egg_ready_at", "care_up_remainder", "care_down_remainder", "stats_revision", "world", "x", "y", "z")) db.createStatement().execute("ALTER TABLE animals DROP COLUMN " + column);
            db.createStatement().execute("PRAGMA user_version=2");
        }
        HusbandryRepository migrated = HusbandryRepository.open(file);
        try { HusbandryAnimal restored = migrated.getAnimal(id).orElseThrow(); assertEquals("Legacy", restored.name()); assertNull(restored.matureAt()); assertNull(restored.statsRevision()); migrated.upsertAnimal(restored); assertEquals(1, migrated.resetStaleStats("new", new Random(3))); }
        finally { migrated.close(); }
    }

    @Test void repositoryWrapsDatabaseFailuresAndClosesFailedInitialization() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("husbandry.db"))) {
            db.createStatement().execute("CREATE TRIGGER reject_animals BEFORE INSERT ON animals BEGIN SELECT RAISE(FAIL,'read only animal'); END");
            assertThrows(SqliteDatabaseException.class, () -> f.repository.upsertAnimal(new HusbandryAnimal(UUID.randomUUID(), "COW", "Rejected")));
            db.createStatement().execute("DROP TRIGGER reject_animals");
            HusbandryAnimal animal = f.offline("COW", "Snapshot"); db.createStatement().execute("CREATE TRIGGER reject_snapshots BEFORE INSERT ON snapshots BEGIN SELECT RAISE(FAIL,'read only snapshot'); END");
            assertThrows(SqliteDatabaseException.class, () -> f.repository.upsertSnapshots(Map.of(animal.uuid(), new byte[]{1}), 1));
            db.createStatement().execute("DROP TABLE snapshots"); assertThrows(SqliteDatabaseException.class, () -> f.repository.deleteSnapshot(animal.uuid())); assertThrows(SqliteDatabaseException.class, () -> f.repository.getSnapshot(animal.uuid()));
        }
        RuntimeException openFailure = new SqliteDatabaseException("Cannot initialize"), closeFailure = new SqliteDatabaseException("Cannot close");
        try (var construction = mockConstruction(SqliteDatabase.class, (db, context) -> { doThrow(openFailure).when(db).execute(anyString()); doThrow(closeFailure).when(db).close(); })) {
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> HusbandryRepository.open(directory.resolve("broken.db").toFile())); assertSame(openFailure, thrown); assertArrayEquals(new Throwable[]{closeFailure}, thrown.getSuppressed());
        }
        try (var construction = mockConstruction(SqliteDatabase.class, (db, context) -> doThrow(openFailure).when(db).execute(anyString()))) {
            assertThrows(RuntimeException.class, () -> HusbandryRepository.open(directory.resolve("uninitialized.db").toFile())); verify(construction.constructed().getFirst()).close();
        }
    }

    @Test void legacyRowReadersTolerateUnavailableOptionalColumnsAndCheckpointFailures() throws Exception {
        HusbandryAnimal animal = f.offline("COW", "Legacy");
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("husbandry.db"))) {
            for (String column : List.of("world", "x", "care_up_remainder")) db.createStatement().execute("ALTER TABLE animals DROP COLUMN " + column);
            HusbandryAnimal read = f.repository.getAnimal(animal.uuid()).orElseThrow(); assertNull(read.world()); assertNull(read.x()); assertEquals(0, read.careUpRemainderSeconds());
        }
        HusbandryRepository closing = spy(HusbandryRepository.open(directory.resolve("closing.db").toFile()));
        doThrow(new SqliteDatabaseException("Checkpoint unavailable")).when(closing).checkpointWal(true); closing.close();
        assertThrows(SqliteDatabaseException.class, () -> closing.exists(animal.uuid()));
    }

    @Test void snapshotRestoreWaitsForEnabledPluginAndRecordsChunkLoadFailureForRetry() {
        HusbandryAnimal animal = f.ownedOffline("Snapshot"); f.repository.upsertSnapshots(Map.of(animal.uuid(), new byte[]{1}), 1);
        World world = mock(World.class); when(world.getName()).thenReturn("farm"); Entity copy = mock(Entity.class); when(copy.getUniqueId()).thenReturn(animal.uuid()); when(copy.getLocation()).thenAnswer(inv -> new Location(world, 0, 65, 0));
        UnsafeValues unsafe = mock(UnsafeValues.class); when(unsafe.deserializeEntity(any(byte[].class), eq(world), eq(true))).thenReturn(copy);
        try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getWorld("farm")).thenReturn(world); bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            CompletableFuture<Chunk> stopped = new CompletableFuture<>(); when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(stopped);
            assertEquals(HusbandrySnapshots.RestoreResult.SCHEDULED, HusbandrySnapshots.restore(f.repository, animal, f.repository.listOwners(animal.uuid()))); when(f.plugin.isEnabled()).thenReturn(false); stopped.complete(mock(Chunk.class)); verify(copy, never()).spawnAt(any(), any());
            when(f.plugin.isEnabled()).thenReturn(true); CompletableFuture<Chunk> failed = new CompletableFuture<>(); when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(failed);
            assertEquals(HusbandrySnapshots.RestoreResult.SCHEDULED, HusbandrySnapshots.restore(f.repository, animal, f.repository.listOwners(animal.uuid()))); failed.completeExceptionally(new IOException("Chunk unavailable")); f.server.getScheduler().performTicks(1);
            assertTrue(HusbandryLocator.isMissing(animal.uuid())); assertTrue(f.repository.getSnapshot(animal.uuid()).isPresent()); verify(copy, never()).spawnAt(any(), any());
        }
    }

    @Test void savedEntityScanHandlesMissingUnreadableAndEmptyDirectories() throws Exception {
        UUID target = UUID.randomUUID(); Path file = directory.resolve("not-a-directory"); Files.write(file, new byte[]{1});
        assertTrue(HusbandryEntityScan.scan(List.of(), Set.of()).complete());
        assertFalse(HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", file.toFile())), Set.of(target)).complete());
        assertFalse(HusbandryEntityScan.scan(List.of(), file.toFile(), Set.of(target)).complete());
        Path entities = Files.createDirectory(directory.resolve("entities")); Files.createDirectory(entities.resolve("r.0.0.mca"));
        assertFalse(HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target)).complete());
    }

    @ParameterizedTest @ValueSource(strings = {"entity", "player"})
    void corruptNbtRootsMakeTheScanIncompleteInsteadOfDeclaringAnimalsMissing(String kind) throws Exception {
        Path entities = Files.createDirectory(directory.resolve("entities")), players = Files.createDirectory(directory.resolve("players"));
        if (kind.equals("entity")) region(entities, new byte[]{1}, 3);
        else Files.write(players.resolve("owner.dat"), gzip(new byte[]{1}));
        var result = HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), players.toFile(), Set.of(UUID.randomUUID()));
        assertFalse(result.complete(), "Unreadable save data must not authorize removal of missing animal records");
    }

    @Test void savedEntityScanSkipsUnrelatedNbtTypesAndReadsLaterValidPositions() throws Exception {
        UUID target = UUID.randomUUID(); Path entities = Files.createDirectory(directory.resolve("entities"));
        byte[] data = nbt(out -> {
            for (int type : new int[]{1, 2, 3, 4, 5, 6}) { out.writeByte(type); out.writeUTF("unrelated" + type); out.write(new byte[switch(type) { case 1 -> 1; case 2 -> 2; case 3, 5 -> 4; default -> 8; }]); }
            out.writeByte(7); out.writeUTF("bytes"); out.writeInt(2); out.write(new byte[2]);
            out.writeByte(8); out.writeUTF("name"); out.writeUTF("World");
            out.writeByte(9); out.writeUTF("list"); out.writeByte(2); out.writeInt(1); out.writeShort(2);
            out.writeByte(10); out.writeUTF("compound"); out.writeByte(1); out.writeUTF("flag"); out.writeByte(1); out.writeByte(0);
            out.writeByte(11); out.writeUTF("ints"); out.writeInt(1); out.writeInt(5);
            out.writeByte(12); out.writeUTF("longs"); out.writeInt(1); out.writeLong(5);
            out.writeByte(9); out.writeUTF("Entities"); out.writeByte(10); out.writeInt(1);
            uuid(out, target);
            out.writeByte(9); out.writeUTF("Pos"); out.writeByte(6); out.writeInt(3); out.writeDouble(-1.1); out.writeDouble(65); out.writeDouble(2.9); out.writeByte(0);
        });
        region(entities, gzip(data), 1); var result = HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target));
        assertTrue(result.complete()); assertEquals(new HusbandryEntityScan.Found("farm", -2, 65, 2), result.found().get(target));
        region(entities, nbt(out -> { out.writeByte(9); out.writeUTF("Entities"); out.writeByte(3); out.writeInt(2); out.writeInt(1); out.writeInt(2); }), 3);
        var wrongEntities = HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target));
        assertTrue(wrongEntities.found().isEmpty()); assertFalse(wrongEntities.complete(), "A nonempty Entities list of integers is corrupt data, not proof that animals are absent");
        region(entities, nbt(out -> { out.writeByte(9); out.writeUTF("Entities"); out.writeByte(0); out.writeInt(0); }), 3);
        assertTrue(HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target)).complete(), "NBT permits an empty list to use the END element type");
        region(entities, nbt(out -> { out.writeByte(99); out.writeUTF("invalid"); }), 3); assertFalse(HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target)).complete());
    }

    @ParameterizedTest @ValueSource(strings = {"negative-list", "uuid-size", "uuid-type", "pos-size", "pos-type", "missing-uuid", "missing-pos", "nonfinite-pos"})
    void malformedEntityIdentityAndPositionCannotAuthorizeGhostDeletion(String problem) throws Exception {
        UUID target = UUID.randomUUID(); Path entities = Files.createDirectory(directory.resolve("entities"));
        region(entities, nbt(out -> {
            out.writeByte(9); out.writeUTF("Entities"); out.writeByte(10); out.writeInt(problem.equals("negative-list") ? -1 : 1);
            if (problem.equals("negative-list")) return;
            if (problem.equals("uuid-size")) { out.writeByte(11); out.writeUTF("UUID"); out.writeInt(2); out.writeLong(1); }
            else if (problem.equals("uuid-type")) { out.writeByte(8); out.writeUTF("UUID"); out.writeUTF(target.toString()); }
            else if (!problem.equals("missing-uuid")) uuid(out, target);
            if (!problem.equals("missing-pos")) {
                out.writeByte(9); out.writeUTF("Pos"); out.writeByte(problem.equals("pos-type") ? 3 : 6); int length = problem.equals("pos-size") ? 2 : 3; out.writeInt(length);
                for (int i = 0; i < length; i++) { if (problem.equals("pos-type")) out.writeInt(i); else out.writeDouble(problem.equals("nonfinite-pos") ? Double.NaN : i); }
            }
            out.writeByte(0);
        }), 3);
        assertFalse(HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), Set.of(target)).complete(), problem + " must leave animal records eligible for retry");
    }

    @ParameterizedTest @ValueSource(strings = {"Entities", "Passengers", "RootVehicle", "Entity"})
    void malformedEntityContainerTagsCannotAuthorizeGhostDeletion(String key) throws Exception {
        UUID target = UUID.randomUUID(); Path entities = Files.createDirectory(directory.resolve("entities")), players = Files.createDirectory(directory.resolve("players"));
        byte[] data = nbt(out -> {
            if (key.equals("Passengers")) {
                out.writeByte(9); out.writeUTF("Entities"); out.writeByte(10); out.writeInt(1); uuid(out, UUID.randomUUID());
                out.writeByte(9); out.writeUTF("Pos"); out.writeByte(6); out.writeInt(3); out.writeDouble(0); out.writeDouble(65); out.writeDouble(0);
            } else if (key.equals("Entity")) { out.writeByte(10); out.writeUTF("RootVehicle"); }
            out.writeByte(3); out.writeUTF(key); out.writeInt(1);
            if (key.equals("Passengers") || key.equals("Entity")) out.writeByte(0);
        });
        if (key.equals("RootVehicle") || key.equals("Entity")) Files.write(players.resolve("owner.dat"), gzip(data));
        else region(entities, data, 3);
        var result = HusbandryEntityScan.scan(List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())), players.toFile(), Set.of(target));
        assertFalse(result.complete(), "A malformed " + key + " tag cannot prove an animal is absent");
        assertFalse(result.found().containsKey(target)); assertFalse(result.ridden().contains(target));
    }

    @Test void savedEntityScanRejectsOversizedExternalAndDecompressedChunks() throws Exception {
        Path entities = Files.createDirectory(directory.resolve("entities")); List<HusbandryEntityScan.WorldDir> worlds = List.of(new HusbandryEntityScan.WorldDir("farm", entities.toFile())); Set<UUID> target = Set.of(UUID.randomUUID());
        region(entities, new byte[0], 0x83);
        try (RandomAccessFile external = new RandomAccessFile(entities.resolve("c.0.0.mcc").toFile(), "rw")) { external.setLength(32L * 1024 * 1024 + 1); }
        assertFalse(HusbandryEntityScan.scan(worlds, target).complete());
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(compressed)) { byte[] block = new byte[64 * 1024]; for (int i = 0; i < 512; i++) zip.write(block); zip.write(1); }
        region(entities, compressed.toByteArray(), 1); assertFalse(HusbandryEntityScan.scan(worlds, target).complete());
    }

    interface NbtBody { void write(DataOutputStream out) throws IOException; }
    byte[] nbt(NbtBody body) throws IOException { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream out = new DataOutputStream(bytes)) { out.writeByte(10); out.writeUTF(""); body.write(out); out.writeByte(0); } return bytes.toByteArray(); }
    void uuid(DataOutputStream out, UUID id) throws IOException { out.writeByte(11); out.writeUTF("UUID"); out.writeInt(4); out.writeInt((int)(id.getMostSignificantBits() >>> 32)); out.writeInt((int)id.getMostSignificantBits()); out.writeInt((int)(id.getLeastSignificantBits() >>> 32)); out.writeInt((int)id.getLeastSignificantBits()); }
    byte[] gzip(byte[] data) throws IOException { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (GZIPOutputStream zip = new GZIPOutputStream(bytes)) { zip.write(data); } return bytes.toByteArray(); }
    void region(Path entities, byte[] payload, int compression) throws IOException {
        byte[] data = new byte[((8197 + payload.length + 4095) / 4096) * 4096]; java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(data);
        bytes.putInt(0, (2 << 8) | 1); bytes.putInt(8192, payload.length + 1); data[8196] = (byte) compression; System.arraycopy(payload, 0, data, 8197, payload.length); Files.write(entities.resolve("r.0.0.mca"), data);
    }
}
