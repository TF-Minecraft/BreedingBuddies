package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.AnvilInventoryMock;
import org.mockito.MockedStatic;

class HusbandryListenersCoverageTest {
    @TempDir Path directory;
    private ServerMock server;
    private net.tfminecraft.breedingbuddies.testsupport.TestCharacters characters;
    private WorldMock world;
    private PlayerMock player, target;
    private BreedingBuddies oldPlugin;
    private HusbandryRepository repository;
    private HusbandryLoaderCoverageTest.ConfigSnapshot configSnapshot;
    private final List<MockedStatic<?>> mocks = new ArrayList<>();
    private MockedStatic<HusbandryHarvest> harvestApi;
    private YamlConfiguration config;
    private final HusbandryBreedListener breeding = new HusbandryBreedListener();
    private final HusbandryHarvestListener harvest = new HusbandryHarvestListener();
    private final HusbandryTamingListener taming = new HusbandryTamingListener();

    @BeforeEach void setUp() throws Exception {
        configSnapshot = new HusbandryLoaderCoverageTest.ConfigSnapshot(); oldPlugin = BreedingBuddies.plugin;
        server = MockBukkit.mock(); world = server.addSimpleWorld("farm"); player = server.addPlayer("Farmer"); target = server.addPlayer("Neighbour");
        player.teleport(new Location(world, 0, 65, 0)); target.teleport(new Location(world, 2, 65, 0));
        BreedingBuddies.plugin = mock(BreedingBuddies.class); when(BreedingBuddies.plugin.getName()).thenReturn("BreedingBuddies"); when(BreedingBuddies.plugin.namespace()).thenReturn("breedingbuddies"); when(BreedingBuddies.plugin.isEnabled()).thenReturn(true);
        characters = new net.tfminecraft.breedingbuddies.testsupport.TestCharacters(BreedingBuddies.plugin);
        characters.activate(player, player.getUniqueId()); characters.activate(target, target.getUniqueId());
        repository = HusbandryRepository.open(directory.resolve("husbandry.db").toFile()); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository);
        HusbandryEntities.clearLoaded(); HusbandryFeedQuality.clear();
        config = new YamlConfiguration(); config.loadFromString("""
                max-animals: 2
                care-max: 100
                initial-genetic-max: 0
                grow-up: 1h
                milk-timer: 10s
                wool-timer: 20s
                items: {tame: tame, co-own: share, feed: feed, neuter: neuter}
                species:
                  COW: {milk: true}
                  GOAT: {milk: true, shear: {drops: {common: [{path: wool}]}}}
                  SHEEP: {shear: {drops: {common: [{path: wool}]}}}
                  CHICKEN: {egg: vanilla}
                  HORSE: {}
                  MULE: {}
                  WOLF: {}
                mounts:
                  HORSE: {min-health: 25, max-health: 25, min-speed: 0.2, max-speed: 0.2, min-jump: 0.6, max-jump: 0.6}
                  MULE: {min-health: 25, max-health: 25, min-speed: 0.2, max-speed: 0.2, min-jump: 0.6, max-jump: 0.6}
                """); reloadConfig();
        ItemAPI itemApi = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        scoped(TLibs.class).when(TLibs::getItemAPI).thenReturn(itemApi);
        when(itemApi.getChecker().checkItemWithPath(any(), anyString())).thenAnswer(call -> {
            ItemStack item = call.getArgument(0); String path = call.getArgument(1);
            if (item == null) return false;
            return switch (path) {
                case "tame" -> item.getType() == Material.NAME_TAG;
                case "share" -> item.getType() == Material.PAPER;
                case "feed" -> item.getType() == Material.DRIED_KELP;
                case "neuter" -> item.getType() == Material.SHEARS && item.getItemMeta().isUnbreakable();
                default -> false;
            };
        });
        harvestApi = mockStatic(HusbandryHarvest.class, CALLS_REAL_METHODS); mocks.add(harvestApi);
        mocks.add(new CookingApiStub().api);
        harvestApi.when(() -> HusbandryHarvest.buildMilk(any(), any())).thenAnswer(call -> new ItemStack(Material.MILK_BUCKET));
        scoped(HusbandryDropRoller.class).when(() -> HusbandryDropRoller.rollShearDrops(any(), any(), anyLong())).thenAnswer(call -> List.of(new ItemStack(Material.WHITE_WOOL, 2)));
    }

    @AfterEach void tearDown() throws Exception {
        HusbandryEntities.clearLoaded(); HusbandryFeedQuality.clear(); repository.close();
        characters.close(); MockBukkit.unmock(); for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
        BreedingBuddies.plugin = oldPlugin; configSnapshot.close();
    }

    @Test void breedingFeedTracksTheUsedHandAndCommitsOnlyWhenLoveModeIsAllowed() {
        Cow cow = spawn(Cow.class); record(cow);
        ItemStack qualityFeed = new ItemStack(Material.WHEAT); var meta = qualityFeed.getItemMeta(); meta.getPersistentDataContainer().set(CookingApiStub.QUALITY, PersistentDataType.INTEGER, 2); qualityFeed.setItemMeta(meta);
        player.getInventory().setItemInOffHand(qualityFeed);
        breeding.onFeed(interact(cow, EquipmentSlot.OFF_HAND));
        var love = new EntityEnterLoveModeEvent(cow, player, 600); breeding.onEnterLove(love);
        assertFalse(love.isCancelled()); assertEquals(0.4, HusbandryFeedQuality.takePair(cow.getUniqueId(), null, System.currentTimeMillis()));
        hold(new ItemStack(Material.DRIED_KELP)); breeding.onFeed(interact(cow)); breeding.onEnterLove(new EntityEnterLoveModeEvent(cow, player, 600));
        assertEquals(1, HusbandryFeedQuality.takePair(cow.getUniqueId(), null, System.currentTimeMillis()));
        breeding.onFeed(interact(spawn(ItemDisplay.class))); breeding.onFeed(interact(spawn(Pig.class)));
    }

    @Test void unavailableUnrecordedNeuteredUnwellAndYoungAnimalsCannotEnterLoveMode() {
        Cow cow = spawn(Cow.class);
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); assertBlockedLove(cow, "Could not breed");
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository); assertBlockedLove(cow, "cannot breed");
        HusbandryAnimal animal = record(cow); animal.setNeutered(true); assertBlockedLove(cow, "Neutered");
        animal.setNeutered(false); animal.setHungrySince(1L); assertBlockedLove(cow, "Hungry");
        animal.setHungrySince(null); animal.setDirtySince(1L); assertBlockedLove(cow, "dirty");
        animal.setDirtySince(null); animal.setMatureAt(System.currentTimeMillis() + 60_000); assertBlockedLove(cow, "growing");
        var unattended = new EntityEnterLoveModeEvent(cow, null, 600); breeding.onEnterLove(unattended); assertTrue(unattended.isCancelled());
        assertEquals(1, HusbandryFeedQuality.takePair(cow.getUniqueId(), null, System.currentTimeMillis()));
    }

    @Test void breedingReportsFirstBlockedParentAndClearsBothLoveModes() {
        Cow mother = spawn(Cow.class), father = spawn(Cow.class), calf = spawn(Cow.class); HusbandryAnimal mom = record(mother), dad = record(father);
        mom.setNeutered(true); dad.setHungrySince(1L); mother.setLoveModeTicks(400); father.setLoveModeTicks(400);
        var first = breed(calf, mother, father, player); breeding.onBreed(first);
        assertTrue(first.isCancelled()); assertTrue(player.nextMessage().contains("Neutered")); assertEquals(0, mother.getLoveModeTicks()); assertEquals(0, father.getLoveModeTicks());
        mom.setNeutered(false); var second = breed(calf, mother, father, player); breeding.onBreed(second); assertTrue(second.isCancelled()); assertTrue(player.nextMessage().contains("Hungry"));
        var unattended = breed(calf, mother, father, null); breeding.onBreed(unattended); assertTrue(unattended.isCancelled()); assertFalse(repository.exists(calf.getUniqueId()));
    }

    @Test void unmanagedBreedingIsUntouchedAndOwnedParentsRequireBothOwnersOrStaff() {
        Pig pig = spawn(Pig.class), mate = spawn(Pig.class), piglet = spawn(Pig.class); var wild = breed(piglet, pig, mate, null); breeding.onBreed(wild); assertFalse(wild.isCancelled()); assertFalse(repository.exists(piglet.getUniqueId()));
        Cow mother = spawn(Cow.class), father = spawn(Cow.class); HusbandryAnimal mom = record(mother), dad = record(father); own(mom, player);
        var noPlayer = breed(spawn(Cow.class), mother, father, null); breeding.onBreed(noPlayer); assertTrue(noPlayer.isCancelled());
        var stranger = breed(spawn(Cow.class), mother, father, target); breeding.onBreed(stranger); assertTrue(stranger.isCancelled()); assertTrue(target.nextMessage().contains("own both"));
        var oneParent = breed(spawn(Cow.class), mother, father, player); breeding.onBreed(oneParent); assertTrue(oneParent.isCancelled()); assertTrue(player.nextMessage().contains("own both"));
        own(dad, player); Cow calf = spawn(Cow.class); var both = breed(calf, mother, father, player); breeding.onBreed(both); assertFalse(both.isCancelled()); assertTrue(repository.exists(calf.getUniqueId()));
        target.setOp(true); Cow staffCalf = spawn(Cow.class); var staff = breed(staffCalf, mother, father, target); breeding.onBreed(staff); assertFalse(staff.isCancelled()); assertTrue(repository.exists(staffCalf.getUniqueId()));
    }

    @Test void babiesPersistGeneticsMaturityAndManagedFlagsWithoutOverwritingExistingRecords() {
        Cow mother = spawn(Cow.class), father = spawn(Cow.class), calf = spawn(Cow.class); HusbandryAnimal mom = record(mother), dad = record(father); mom.setGenetics(20); dad.setGenetics(40);
        long before = System.currentTimeMillis(); breeding.onBreed(breed(calf, mother, father, null));
        HusbandryAnimal baby = repository.getAnimal(calf.getUniqueId()).orElseThrow();
        assertEquals(30, baby.genetics()); assertEquals(0, baby.care()); assertEquals(HusbandryAnimalState.UNTAMED, baby.state()); assertEquals("Cow", baby.name()); assertEquals(HusbandryConfig.statsRevision(), baby.statsRevision());
        assertTrue(baby.matureAt() >= before + 3_600_000); assertTrue(baby.lastProcessedAt() >= before); assertNotNull(baby.loadedVisitStart()); assertNull(baby.unloadedAt());
        assertTrue(HusbandryEntities.isManaged(calf)); assertTrue(calf.isPersistent()); assertFalse(calf.getRemoveWhenFarAway()); assertFalse(calf.isAdult()); assertTrue(calf.getAgeLock());
        HusbandryAnimal canonical = HusbandryEntities.getLoaded(calf.getUniqueId()).orElseThrow(); canonical.setName("Retained"); repository.upsertAnimal(canonical);
        breeding.onBreed(breed(calf, mother, father, player)); assertEquals("Retained", repository.getAnimal(calf.getUniqueId()).orElseThrow().name());
    }

    @Test void mountBreedingSchedulesStatsAndIgnoresRemovedOrMissingChildren() throws Exception {
        Horse mother = spawn(Horse.class), father = spawn(Horse.class), foal = spawn(Horse.class); record(mother); record(father);
        breeding.onBreed(breed(foal, mother, father, player)); server.getScheduler().performOneTick(); assertEquals(25, foal.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        Horse removed = spawn(Horse.class); breeding.onBreed(breed(removed, mother, father, player)); removed.remove(); server.getScheduler().performOneTick(); assertFalse(removed.isValid());
        Horse lost = spawn(Horse.class); breeding.onBreed(breed(lost, mother, father, player)); HusbandryEntities.evict(lost.getUniqueId()); repository.deleteAnimal(lost.getUniqueId()); server.getScheduler().performOneTick(); assertFalse(repository.exists(lost.getUniqueId()));
        config.set("species.HORSE", null); reloadConfig();
        Donkey donkey = spawn(Donkey.class); Mule mule = spawn(Mule.class); breeding.onBreed(breed(mule, mother, donkey, null)); assertTrue(repository.exists(mule.getUniqueId()));
        // Vanilla horse/donkey breeding can produce a managed mule from two unmanaged parents.
        Horse wildHorse = spawn(Horse.class); Mule wildMule = spawn(Mule.class); breeding.onBreed(breed(wildMule, wildHorse, donkey, null)); assertEquals(0, repository.getAnimal(wildMule.getUniqueId()).orElseThrow().genetics());
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); Mule unavailable = spawn(Mule.class); breeding.onBreed(breed(unavailable, wildHorse, donkey, null)); server.getScheduler().performOneTick(); assertFalse(repository.exists(unavailable.getUniqueId()));
    }

    @Test void milkingIgnoresUnrelatedHandsItemsEntitiesAndUnconfiguredSpecies() {
        Cow cow = spawn(Cow.class); hold(new ItemStack(Material.BUCKET));
        var boots = interact(cow, EquipmentSlot.FEET); harvest.onMilk(boots); assertFalse(boots.isCancelled());
        hold(null); var empty = interact(cow); harvest.onMilk(empty); assertFalse(empty.isCancelled());
        hold(new ItemStack(Material.STONE)); harvest.onMilk(interact(cow));
        hold(new ItemStack(Material.BUCKET)); var display = interact(spawn(ItemDisplay.class)); harvest.onMilk(display); assertFalse(display.isCancelled());
        var pig = interact(spawn(Pig.class)); harvest.onMilk(pig); assertFalse(pig.isCancelled());
        var sheep = interact(spawn(Sheep.class)); harvest.onMilk(sheep); assertFalse(sheep.isCancelled());
    }

    @Test void immatureUnrecordedAndCoolingDownAnimalsKeepTheirBuckets() {
        Cow cow = spawn(Cow.class); hold(new ItemStack(Material.BUCKET));
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); var noRepo = interact(cow); harvest.onMilk(noRepo); assertTrue(noRepo.isCancelled());
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository); harvest.onMilk(interact(cow)); assertEquals(Material.BUCKET, player.getInventory().getItemInMainHand().getType());
        HusbandryAnimal animal = record(cow); animal.setMatureAt(System.currentTimeMillis() + 60_000); harvest.onMilk(interact(cow)); assertTrue(player.nextMessage().contains("growing"));
        animal.setMatureAt(null); animal.setLastMilkAt(System.currentTimeMillis() - 2_000); harvest.onMilk(interact(cow)); assertTrue(player.nextMessage().contains("not ready"));
        animal.setLastMilkAt(System.currentTimeMillis()); harvest.onMilk(interact(cow)); assertNull(player.nextMessage());
        animal.setLastMilkAt(null); harvestApi.when(() -> HusbandryHarvest.buildMilk(any(), any())).thenReturn(null); harvest.onMilk(interact(cow)); assertEquals(Material.BUCKET, player.getInventory().getItemInMainHand().getType()); assertNull(animal.lastMilkAt());
    }

    @Test void milkingConsumesExactlyOneBucketPersistsCooldownAndSupportsTheOffHand() {
        Cow cow = spawn(Cow.class); HusbandryAnimal animal = record(cow); hold(new ItemStack(Material.BUCKET));
        long before = System.currentTimeMillis(); var event = interact(cow); harvest.onMilk(event);
        assertTrue(event.isCancelled()); assertEquals(Material.MILK_BUCKET, player.getInventory().getItemInMainHand().getType()); assertTrue(repository.getAnimal(cow.getUniqueId()).orElseThrow().lastMilkAt() >= before);
        animal.setLastMilkAt(System.currentTimeMillis() - 20_000); hold(new ItemStack(Material.STONE)); player.getInventory().setItemInOffHand(new ItemStack(Material.BUCKET)); harvest.onMilk(interact(cow, EquipmentSlot.OFF_HAND)); assertEquals(Material.MILK_BUCKET, player.getInventory().getItemInOffHand().getType());
        animal.setLastMilkAt(null); hold(new ItemStack(Material.BUCKET)); player.getInventory().setItemInOffHand(new ItemStack(Material.BUCKET)); harvest.onMilk(interact(cow, EquipmentSlot.OFF_HAND)); assertEquals(Material.BUCKET, player.getInventory().getItemInOffHand().getType()); assertNull(animal.lastMilkAt());
    }

    @Test void stackedAndCreativeBucketsReceiveMilkInInventoryAndOverflowDropsAtThePlayer() {
        Goat goat = spawn(Goat.class); HusbandryAnimal animal = record(goat); hold(new ItemStack(Material.BUCKET, 2)); harvest.onMilk(interact(goat)); assertEquals(1, player.getInventory().getItemInMainHand().getAmount()); assertEquals(1, player.getInventory().all(Material.MILK_BUCKET).size());
        animal.setLastMilkAt(null); player.getInventory().clear(); player.setGameMode(GameMode.CREATIVE); hold(new ItemStack(Material.BUCKET)); harvest.onMilk(interact(goat)); assertEquals(Material.BUCKET, player.getInventory().getItemInMainHand().getType()); assertEquals(1, player.getInventory().all(Material.MILK_BUCKET).size());
        animal.setLastMilkAt(null); player.setGameMode(GameMode.SURVIVAL); for (int slot = 0; slot < player.getInventory().getSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64)); hold(new ItemStack(Material.BUCKET, 2));
        harvest.onMilk(interact(goat)); assertTrue(world.getEntitiesByClass(Item.class).stream().anyMatch(item -> item.getItemStack().getType() == Material.MILK_BUCKET)); assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void vanillaMilkFillIsCancelledWithoutAffectingWaterBuckets() {
        var block = world.getBlockAt(0, 64, 0);
        var milk = new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.MILK_BUCKET, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND); harvest.onVanillaMilkFill(milk); assertTrue(milk.isCancelled());
        var water = new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND); harvest.onVanillaMilkFill(water); assertFalse(water.isCancelled());
    }

    @Test void shearsInteractionFiltersNeuteringToolsSheepAndMissingRecordsBeforeGrantingWool() {
        Goat goat = spawn(Goat.class); hold(new ItemStack(Material.SHEARS)); harvest.onShearsInteract(interact(goat, EquipmentSlot.OFF_HAND));
        hold(null); harvest.onShearsInteract(interact(goat));
        ItemStack tool = new ItemStack(Material.SHEARS); var meta = tool.getItemMeta(); meta.setUnbreakable(true); tool.setItemMeta(meta); hold(tool); harvest.onShearsInteract(interact(goat));
        hold(new ItemStack(Material.SHEARS)); harvest.onShearsInteract(interact(spawn(ItemDisplay.class))); harvest.onShearsInteract(interact(spawn(Sheep.class))); harvest.onShearsInteract(interact(spawn(Pig.class))); harvest.onShearsInteract(interact(spawn(Cow.class)));
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); harvest.onShearsInteract(interact(goat)); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository); harvest.onShearsInteract(interact(goat));
        HusbandryAnimal animal = record(goat); long before = System.currentTimeMillis(); var ready = interact(goat); harvest.onShearsInteract(ready);
        assertTrue(ready.isCancelled()); assertEquals(2, player.getInventory().all(Material.WHITE_WOOL).values().stream().mapToInt(ItemStack::getAmount).sum()); assertTrue(animal.woolReadyAt() >= before + 20_000); assertNotNull(repository.getAnimal(goat.getUniqueId()).orElseThrow().woolReadyAt());
    }

    @Test void shearEventsRetainVanillaSheepDropsAndApplyManagedGoatCooldowns() {
        harvest.onShear(shear(spawn(Pig.class))); harvest.onShear(shear(spawn(Cow.class)));
        Goat goat = spawn(Goat.class); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); harvest.onShear(shear(goat)); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository); harvest.onShear(shear(goat));
        record(goat); var goatEvent = shear(goat); harvest.onShear(goatEvent); assertTrue(goatEvent.isCancelled());
        Sheep sheep = spawn(Sheep.class); HusbandryAnimal animal = record(sheep); var sheepEvent = shear(sheep); harvest.onShear(sheepEvent); assertFalse(sheepEvent.isCancelled()); assertNotNull(animal.woolReadyAt());
    }

    @Test void onlyManagedConfiguredChickenEggsAreReplacedByTheHusbandryTimer() throws Exception {
        Chicken chicken = spawn(Chicken.class); Item egg = world.dropItem(new Location(world, 0, 65, 0), new ItemStack(Material.EGG));
        var otherAnimal = new EntityDropItemEvent(spawn(Cow.class), egg); harvest.onDropEgg(otherAnimal); assertFalse(otherAnimal.isCancelled());
        Item feather = world.dropItem(new Location(world, 0, 65, 0), new ItemStack(Material.FEATHER)); var nonEgg = new EntityDropItemEvent(chicken, feather); harvest.onDropEgg(nonEgg); assertFalse(nonEgg.isCancelled());
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); var unavailable = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(unavailable); assertFalse(unavailable.isCancelled()); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository);
        var unknown = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(unknown); assertFalse(unknown.isCancelled());
        record(chicken); var managed = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(managed); assertTrue(managed.isCancelled());
        HusbandryEntities.evict(chicken.getUniqueId()); var persisted = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(persisted); assertTrue(persisted.isCancelled());
        config.set("species.CHICKEN.egg", ""); reloadConfig(); var disabled = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(disabled); assertFalse(disabled.isCancelled());
        config.set("species.CHICKEN", null); reloadConfig(); var removed = new EntityDropItemEvent(chicken, egg); harvest.onDropEgg(removed); assertFalse(removed.isCancelled());
    }

    @Test void anvilNamesAreStoredOnlyForNamedTamingItems() {
        AnvilView view = mock(AnvilView.class); when(view.getTopInventory()).thenReturn(new AnvilInventoryMock(null));
        taming.onPrepareAnvil(new PrepareAnvilEvent(view, null)); taming.onPrepareAnvil(new PrepareAnvilEvent(view, new ItemStack(Material.STONE)));
        ItemStack tag = new ItemStack(Material.NAME_TAG); var missing = new PrepareAnvilEvent(view, tag); taming.onPrepareAnvil(missing); assertNull(HusbandryItems.tameName(missing.getResult()));
        when(view.getRenameText()).thenReturn(" "); taming.onPrepareAnvil(missing); assertNull(HusbandryItems.tameName(missing.getResult()));
        when(view.getRenameText()).thenReturn("  Daisy  "); var named = new PrepareAnvilEvent(view, tag); taming.onPrepareAnvil(named); assertEquals("Daisy", HusbandryItems.tameName(named.getResult()));
    }

    @Test void tamingRequiresAMainHandNameSupportedSpeciesAndVanillaTamingFirst() {
        Cow cow = spawn(Cow.class); hold(new ItemStack(Material.STONE)); var unrelated = interact(cow); taming.onInteractEntity(unrelated); assertFalse(unrelated.isCancelled());
        hold(namedTag("Daisy")); var offHand = interact(cow, EquipmentSlot.OFF_HAND); taming.onInteractEntity(offHand); assertFalse(offHand.isCancelled());
        var decoration = interact(spawn(ItemDisplay.class)); taming.onInteractEntity(decoration); assertTrue(decoration.isCancelled());
        hold(new ItemStack(Material.NAME_TAG)); taming.onInteractEntity(interact(cow)); assertTrue(player.nextMessage().contains("anvil"));
        hold(namedTag("Wolfie")); Wolf wolf = spawn(Wolf.class); taming.onInteractEntity(interact(wolf)); assertTrue(player.nextMessage().contains("Tame this animal first"));
        taming.onInteractEntity(interact(spawn(Pig.class))); assertTrue(player.nextMessage().contains("cannot be tamed"));
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); taming.onInteractEntity(interact(cow)); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void tamingAndRenamingPersistOwnersNamesAndExactlyOneItemUse() {
        Cow cow = spawn(Cow.class); hold(namedTag("Daisy")); taming.onInteractEntity(interact(cow));
        HusbandryAnimal animal = repository.getAnimal(cow.getUniqueId()).orElseThrow(); assertEquals("Daisy", animal.name()); assertEquals(HusbandryAnimalState.OWNED, animal.state()); assertTrue(HusbandryOwnershipService.isOwner(player, animal.uuid())); assertEquals("Daisy", cow.getCustomName()); assertTrue(HusbandryEntities.isManaged(cow)); assertEquals(1, player.getInventory().getItemInMainHand().getAmount()); assertTrue(player.nextMessage().contains("Tamed Daisy"));
        hold(namedTag("Buttercup")); taming.onInteractEntity(interact(cow)); assertEquals("Buttercup", repository.getAnimal(cow.getUniqueId()).orElseThrow().name()); assertTrue(player.nextMessage().contains("Renamed to Buttercup"));
        Wolf wolf = spawn(Wolf.class); wolf.setTamed(true); hold(namedTag("Wolfie")); taming.onInteractEntity(interact(wolf)); assertTrue(repository.exists(wolf.getUniqueId())); assertTrue(player.nextMessage().contains("Tamed Wolfie"));
    }

    @Test void tamingRejectsWildAndRecordedAnimalsAtCapacityAndAnimalsOwnedByOthers() {
        own(record(spawn(Cow.class)), player); own(record(spawn(Cow.class)), player); hold(namedTag("Too many"));
        Cow wild = spawn(Cow.class); taming.onInteractEntity(interact(wild)); assertFalse(repository.exists(wild.getUniqueId())); assertTrue(player.nextMessage().contains("too many"));
        Cow recorded = spawn(Cow.class); record(recorded); taming.onInteractEntity(interact(recorded)); assertTrue(player.nextMessage().contains("too many")); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        Cow others = spawn(Cow.class); own(record(others), target); taming.onInteractEntity(interact(others)); assertTrue(player.nextMessage().contains("already has an owner"));
    }

    @Test void cancelledEntityInteractionsCannotClaimOrConsumeATamingItem() {
        Cow cow = spawn(Cow.class); hold(namedTag("Protected"));
        server.getPluginManager().registerEvents(taming, MockBukkit.createMockPlugin("HusbandryTest"));
        var denied = interact(cow); denied.setCancelled(true); server.getPluginManager().callEvent(denied);
        assertFalse(repository.exists(cow.getUniqueId()), "An earlier listener denied this interaction"); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void sharingLinksOnlyOwnedRecordsAndUsesTheFallbackSpeciesName() {
        Cow cow = spawn(Cow.class); hold(new ItemStack(Material.PAPER, 2));
        when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); taming.onInteractEntity(interact(cow)); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(repository);
        taming.onInteractEntity(interact(cow)); assertTrue(player.nextMessage().contains("cannot be shared"));
        HusbandryAnimal animal = record(cow); taming.onInteractEntity(interact(cow)); assertTrue(player.nextMessage().contains("not your animal"));
        own(animal, player); animal.setName(null); repository.upsertAnimal(animal); taming.onInteractEntity(interact(cow));
        assertEquals(animal.uuid().toString(), HusbandryItems.linkedAnimal(player.getInventory().getItemInMainHand())); assertTrue(player.nextMessage().contains("Linked to Cow"));
        animal.setName("Daisy"); taming.onInteractEntity(interact(cow)); assertTrue(player.nextMessage().contains("Linked to Daisy"));
        hold(new ItemStack(Material.PAPER)); var decoration = interact(spawn(ItemDisplay.class)); taming.onInteractEntity(decoration); assertTrue(decoration.isCancelled());
    }

    @Test void sharingReportsMissingMalformedAndUnresolvableTokensWithoutConsumption() {
        for (String linked : Arrays.asList(null, " ", "bad-uuid", UUID.randomUUID().toString())) {
            hold(token(linked)); taming.onInteractEntity(interact(target));
            assertNotNull(player.nextMessage()); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        }
        HusbandryAnimal animal = record(spawn(Cow.class)); hold(token(animal.uuid().toString())); when(BreedingBuddies.plugin.getHusbandryRepository()).thenReturn(null); taming.onInteractEntity(interact(target)); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void sharingRequiresAnOwnerAndRejectsExistingOwnersAndFullRecipients() {
        HusbandryAnimal animal = record(spawn(Cow.class)); hold(token(animal.uuid().toString())); taming.onInteractEntity(interact(target)); assertTrue(player.nextMessage().contains("not your animal"));
        own(animal, player); own(animal, target); taming.onInteractEntity(interact(target)); assertTrue(player.nextMessage().contains("Already an owner"));
        repository.deleteCharacterOwner(animal.uuid(), target.getUniqueId()); own(record(spawn(Cow.class)), target); own(record(spawn(Cow.class)), target); taming.onInteractEntity(interact(target)); assertTrue(player.nextMessage().contains("too many")); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void sharingToAnotherPlayerPersistsCoOwnershipAndConsumesOneToken() {
        HusbandryAnimal animal = record(spawn(Cow.class)); own(animal, player); hold(token(animal.uuid().toString())); taming.onInteractEntity(interact(target));
        assertTrue(HusbandryOwnershipService.isOwner(target, animal.uuid())); assertEquals(1, player.getInventory().getItemInMainHand().getAmount()); assertTrue(player.nextMessage().contains("Added Neighbour")); assertTrue(target.nextMessage().contains("co-owner"));
    }

    @Test void aRecipientCanAcceptALinkedTokenByClickingAnyEntityOrUsingItInTheAir() {
        HusbandryAnimal first = record(spawn(Cow.class)); first.setName(null); repository.upsertAnimal(first); own(first, target); hold(token(first.uuid().toString())); taming.onInteractEntity(interact(spawn(ItemDisplay.class)));
        assertTrue(HusbandryOwnershipService.isOwner(player, first.uuid()));
        assertEquals("§aYou are now a co-owner of animal.", player.nextMessage());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        HusbandryAnimal second = record(spawn(Cow.class)); own(second, target); hold(token(second.uuid().toString()));
        var use = use(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND); taming.onInteractUse(use); assertTrue(use.isCancelled()); assertTrue(HusbandryOwnershipService.isOwner(player, second.uuid())); assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void tokenUseIgnoresOffHandOtherActionsItemsAndTokensAlreadyOwned() {
        HusbandryAnimal animal = record(spawn(Cow.class)); own(animal, player); hold(token(animal.uuid().toString()));
        taming.onInteractUse(use(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND)); taming.onInteractUse(use(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND)); taming.onInteractUse(use(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
        taming.onInteractUse(use(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND)); assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        hold(new ItemStack(Material.STONE)); taming.onInteractUse(use(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND)); assertNull(player.nextMessage());
    }

    private <T extends Entity> T spawn(Class<T> type) {
        T entity = spy(world.spawn(new Location(world, 1, 65, 1), type));
        if (entity instanceof LivingEntity living) {
            // MockBukkit does not implement this server despawn flag; retain its real value here.
            boolean[] removeWhenFar = {true};
            doAnswer(call -> { removeWhenFar[0] = call.getArgument(0); return null; }).when(living).setRemoveWhenFarAway(anyBoolean());
            doAnswer(call -> removeWhenFar[0]).when(living).getRemoveWhenFarAway();
        }
        return entity;
    }
    private <T> MockedStatic<T> scoped(Class<T> type) { MockedStatic<T> result = mockStatic(type); mocks.add(result); return result; }
    private HusbandryAnimal record(LivingEntity entity) { HusbandryAnimal animal = new HusbandryAnimal(entity.getUniqueId(), entity.getType().name(), HusbandryEntities.displayName(entity.getType())); repository.upsertAnimal(animal); HusbandryEntities.putLoaded(animal); return animal; }
    private void own(HusbandryAnimal animal, Player owner) { repository.upsertOwner(new HusbandryOwner(animal.uuid(), owner.getUniqueId(), owner.getUniqueId(), "owner")); }
    private void hold(ItemStack item) { player.getInventory().setItemInMainHand(item); }
    private ItemStack namedTag(String name) { ItemStack item = new ItemStack(Material.NAME_TAG, 2); HusbandryItems.setTameName(item, name); return item; }
    private ItemStack token(String linked) { ItemStack item = new ItemStack(Material.PAPER, 2); if (linked != null) HusbandryItems.setLinkedAnimal(item, linked, null); return item; }
    private PlayerInteractEntityEvent interact(Entity clicked) { return interact(clicked, EquipmentSlot.HAND); }
    private PlayerInteractEntityEvent interact(Entity clicked, EquipmentSlot hand) { return new PlayerInteractEntityEvent(player, clicked, hand); }
    private PlayerShearEntityEvent shear(Entity entity) { return new PlayerShearEntityEvent(player, entity, new ItemStack(Material.SHEARS), EquipmentSlot.HAND, List.of()); }
    private EntityBreedEvent breed(LivingEntity child, LivingEntity mother, LivingEntity father, LivingEntity breeder) { return new EntityBreedEvent(child, mother, father, breeder, new ItemStack(Material.WHEAT), 1); }
    private PlayerInteractEvent use(Action action, EquipmentSlot hand) { return new PlayerInteractEvent(player, action, player.getInventory().getItemInMainHand(), action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK ? world.getBlockAt(0, 64, 0) : null, BlockFace.UP, hand); }
    private void assertBlockedLove(Cow cow, String message) { cow.setLoveModeTicks(500); HusbandryFeedQuality.offer(cow.getUniqueId(), 0.2, System.currentTimeMillis()); var event = new EntityEnterLoveModeEvent(cow, player, 600); breeding.onEnterLove(event); assertTrue(event.isCancelled()); assertEquals(0, cow.getLoveModeTicks()); assertTrue(player.nextMessage().contains(message)); }
    private void reloadConfig() throws Exception { Path file = directory.resolve("husbandry.yml"); config.save(file.toFile()); new HusbandryLoader().load(file.toFile()); }
}
