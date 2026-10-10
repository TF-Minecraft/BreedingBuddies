package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.AnimalStates;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.ItemUtils;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockbukkit.mockbukkit.matcher.sound.SoundReceiverSoundHeardMatcher.hasHeard;
import static org.junit.jupiter.api.Assertions.*;

/** Collecting bundles from animals and opening them for prizes. */
class BundleListenersTest extends PluginTestBase {
    private BundleCollector collector;
    private BundleOpener opener;
    private PlayerMock alice;

    @BeforeEach
    void createListeners() {
        collector = new BundleCollector(plugin);
        opener = new BundleOpener(plugin);
        alice = player();
        stubMmoItems(Map.of(
                "PETS:COMMON_ANIMAL_BUNDLE", mmoItem("COMMON_ANIMAL_BUNDLE"),
                "PETS:RARE_ANIMAL_BUNDLE", mmoItem("RARE_ANIMAL_BUNDLE"),
                "MATERIALS:LEATHER", new ItemStack(Material.LEATHER)));
    }

    private static YamlConfiguration bundles(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        BundleCollector.setBundlesConfig(config);
        BundleOpener.setBundlesConfig(config);
        FarmAnimal.setBundlesConfig(config);
        return config;
    }

    private PlayerInteractEntityEvent collect(Entity animal, EquipmentSlot hand) {
        alice.getInventory().setItemInMainHand(mmoItem("COLLECTOR_ITEM"));
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(alice, animal, hand);
        collector.onInteract(event);
        return event;
    }

    private ItemStack[] storage() {
        return Arrays.stream(alice.getInventory().getStorageContents()).skip(1).filter(Objects::nonNull).toArray(ItemStack[]::new);
    }

    private ItemStack onlyCollected() {
        ItemStack[] items = storage();
        assertEquals(1, items.length, Arrays.toString(items));
        return items[0];
    }

    private FarmAnimal ownedCow(int friendship, int genetics) {
        FarmAnimal animal = own(alice, spawn(Cow.class));
        animal.setFriendshipPoints(friendship);
        animal.setGeneticPoints(genetics);
        return animal;
    }

    private Entity entityOf(FarmAnimal animal) {
        return server.getEntity(animal.getUuid());
    }

    @Test
    void aHappyAnimalGivesABundleMarkedWithItsSpecies() {
        FarmAnimal daisy = ownedCow(1000, 1000);

        PlayerInteractEntityEvent event = collect(entityOf(daisy), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        ItemStack bundle = onlyCollected();
        assertEquals("COMMON_ANIMAL_BUNDLE", ItemUtils.getItemId(bundle));
        assertEquals("COW", ItemUtils.getDataFromItem(bundle, plugin));
        assertNotNull(daisy.getLastRewardCollected());
        assertThat(alice, hasHeard("block.amethyst_block.step"));
        assertFalse(daisy.canCollectReward(), "the cooldown has started");
    }

    @Test
    void wellBalancedHighStatsEarnTheRareTier() {
        FarmAnimal star = ownedCow(8000, 8000);
        FarmAnimal lopsided = ownedCow(8000, 2000);

        assertEquals("rare", collector.calculateTier(entityOf(star)));
        assertEquals("common", collector.calculateTier(entityOf(lopsided)), "the gap between the stats counts against it");

        collect(entityOf(star), EquipmentSlot.HAND);
        assertEquals("RARE_ANIMAL_BUNDLE", ItemUtils.getItemId(onlyCollected()));
    }

    @Test
    void unhappyOrRestingAnimalsGiveNothing() {
        FarmAnimal sad = ownedCow(1000, 1000);
        sad.setState(AnimalStates.SAD);
        FarmAnimal resting = ownedCow(1000, 1000);
        resting.setLastRewardCollected(new Date(System.currentTimeMillis() - Duration.ofHours(1).toMillis()));

        PlayerInteractEntityEvent event = collect(entityOf(sad), EquipmentSlot.HAND);
        collect(entityOf(resting), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertEquals(0, storage().length);
        assertThat(alice, hasHeard("entity.cow.ambient"));
    }

    @Test
    void aFullInventoryKeepsTheBundleWaiting() {
        FarmAnimal daisy = ownedCow(1000, 1000);
        for (int slot = 1; slot < 36; slot++) {
            alice.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }

        collect(entityOf(daisy), EquipmentSlot.HAND);

        assertEquals(Messages.lacksSpace, alice.nextMessage());
        assertNull(daisy.getLastRewardCollected());
        assertTrue(daisy.canCollectReward());
    }

    @Test
    void collectingNeedsTheOwnerTheItemTheMainHandAndASpeciesWithBundles() {
        FarmAnimal daisy = ownedCow(1000, 1000);
        Horse horse = spawn(Horse.class);
        own(alice, horse);
        Pig pig = spawn(Pig.class);
        own(alice, pig);
        Cow theirs = spawn(Cow.class);
        own(player(), theirs);

        PlayerInteractEntityEvent offHand = collect(entityOf(daisy), EquipmentSlot.OFF_HAND);
        PlayerInteractEntityEvent onHorse = collect(horse, EquipmentSlot.HAND);
        PlayerInteractEntityEvent onPig = collect(pig, EquipmentSlot.HAND);
        PlayerInteractEntityEvent onTheirs = collect(theirs, EquipmentSlot.HAND);
        alice.getInventory().setItemInMainHand(new ItemStack(Material.HOPPER));
        PlayerInteractEntityEvent wrongItem = new PlayerInteractEntityEvent(alice, entityOf(daisy), EquipmentSlot.HAND);
        collector.onInteract(wrongItem);

        for (PlayerInteractEntityEvent event : new PlayerInteractEntityEvent[] {offHand, onHorse, onPig, onTheirs, wrongItem}) {
            assertFalse(event.isCancelled());
        }
        assertEquals(0, storage().length);
        assertNull(daisy.getLastRewardCollected());
    }

    @Test
    void mountsWithBundlesKeepTheirVanillaMenuClosedWhileCollecting() throws Exception {
        bundles("""
                HORSE:
                  bundlesEnabled: true
                  bundles:
                    only:
                      bundleItem: {type: vanilla, itemID: saddle}
                """);
        Horse horse = spawn(Horse.class);
        own(alice, horse);

        PlayerInteractEntityEvent event = collect(horse, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        ItemStack bundle = onlyCollected();
        assertEquals(Material.SADDLE, bundle.getType());
        assertEquals("HORSE", ItemUtils.getDataFromItem(bundle, plugin));
    }

    @Test
    void misconfiguredBundleItemsFallBackToAPlaceholder() throws Exception {
        bundles("""
                COW:
                  bundlesEnabled: true
                  bundles:
                    only:
                      bundleItem: {type: vanilla, itemID: not_an_item}
                PIG:
                  bundlesEnabled: true
                  bundles:
                    only:
                      bundleItem: {type: crate}
                SHEEP:
                  bundlesEnabled: true
                  bundles:
                    only:
                      bundleItem: {type: mmoitem, mmoitemType: PETS, mmoitemID: MISSING}
                """);
        FarmAnimal daisy = ownedCow(0, 0);
        Pig pig = spawn(Pig.class);
        own(alice, pig);
        org.bukkit.entity.Sheep sheep = spawn(org.bukkit.entity.Sheep.class);
        own(alice, sheep);

        collect(entityOf(daisy), EquipmentSlot.HAND);
        collect(pig, EquipmentSlot.HAND);
        collect(sheep, EquipmentSlot.HAND);

        ItemStack[] items = storage();
        assertEquals(3, Arrays.stream(items).mapToInt(ItemStack::getAmount).sum());
        for (ItemStack item : items) {
            assertEquals(Material.DIRT, item.getType());
        }
    }

    // Opening

    private PlayerInteractEvent open(ItemStack bundle, Action action) {
        PlayerInteractEvent event = new PlayerInteractEvent(alice, action, bundle, null, BlockFace.SELF, EquipmentSlot.HAND);
        opener.onRightClick(event);
        return event;
    }

    private ItemStack bundleFor(String species, ItemStack item) {
        ItemUtils.storeDataInItem(item, species, plugin);
        return item;
    }

    private void prizeBundles() throws Exception {
        bundles("""
                COW:
                  bundlesEnabled: true
                  bundles:
                    small:
                      bundleItem: {type: vanilla, itemID: bundle}
                      prizes:
                        beef: {type: vanilla, itemID: beef, amount: 3, weight: 1}
                        nothing: {type: vanilla, itemID: diamond, amount: 64, weight: 0}
                    big:
                      bundleItem: {type: mmoitem, mmoitemType: PETS, mmoitemID: RARE_ANIMAL_BUNDLE}
                      prizes:
                        leather: {type: mmoitem, mmoitemType: MATERIALS, mmoitemID: LEATHER, amount: 2, weight: 5}
                    broken:
                      bundleItem: {type: vanilla, itemID: paper}
                      prizes:
                        typo: {type: vanilla, itemID: not_an_item, amount: 1, weight: 1}
                    missing:
                      bundleItem: {type: vanilla, itemID: stick}
                      prizes:
                        gone: {type: mmoitem, mmoitemType: MATERIALS, mmoitemID: GONE, amount: 1, weight: 1}
                    odd:
                      bundleItem: {type: crate}
                      prizes:
                        crate: {type: crate, amount: 1, weight: 1}
                HORSE:
                  bundlesEnabled: false
                """);
    }

    @Test
    void openingABundleSpendsItAndGivesItsPrize() throws Exception {
        prizeBundles();
        ItemStack bundle = bundleFor("COW", new ItemStack(Material.BUNDLE, 2));

        for (int i = 0; i < 2; i++) {
            open(bundle, i == 0 ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK);
        }

        assertEquals(0, bundle.getAmount());
        assertTrue(alice.getInventory().contains(Material.BEEF, 6), "weightless prizes are never drawn");
        assertFalse(alice.getInventory().contains(Material.DIAMOND));
        assertThat(alice, hasHeard(Sound.ENTITY_EXPERIENCE_ORB_PICKUP));
    }

    @Test
    void mmoItemBundlesGiveMmoItemPrizes() throws Exception {
        prizeBundles();
        ItemStack bundle = bundleFor("COW", mmoItem("RARE_ANIMAL_BUNDLE"));

        open(bundle, Action.RIGHT_CLICK_AIR);

        assertEquals(0, bundle.getAmount());
        assertTrue(alice.getInventory().contains(Material.LEATHER, 2));
    }

    @Test
    void prizesThatCannotBeMadeBecomeAPlaceholder() throws Exception {
        prizeBundles();

        open(bundleFor("COW", new ItemStack(Material.PAPER)), Action.RIGHT_CLICK_AIR);
        open(bundleFor("COW", new ItemStack(Material.STICK)), Action.RIGHT_CLICK_AIR);

        assertTrue(alice.getInventory().contains(Material.DIRT, 2));
    }

    @Test
    void onlyMarkedBundleItemsOfAConfiguredTierOpen() throws Exception {
        prizeBundles();
        ItemStack unmarked = new ItemStack(Material.BUNDLE);
        ItemStack wrongItem = bundleFor("COW", new ItemStack(Material.APPLE));
        ItemStack noBundles = bundleFor("HORSE", new ItemStack(Material.BUNDLE));
        ItemStack renamedToken = bundleFor("Daisy", mmoItem("TAMING_ITEM"));
        ItemStack leftClicked = bundleFor("COW", new ItemStack(Material.BUNDLE));

        open(unmarked, Action.RIGHT_CLICK_AIR);
        open(wrongItem, Action.RIGHT_CLICK_AIR);
        open(noBundles, Action.RIGHT_CLICK_AIR);
        open(renamedToken, Action.RIGHT_CLICK_AIR);
        open(leftClicked, Action.LEFT_CLICK_AIR);
        open(null, Action.RIGHT_CLICK_AIR);

        for (ItemStack item : new ItemStack[] {unmarked, wrongItem, noBundles, renamedToken, leftClicked}) {
            assertEquals(1, item.getAmount());
        }
        assertTrue(alice.getInventory().isEmpty());
    }

    @Test
    void collectedBundlesFromTheDefaultConfigCanBeOpened() {
        FarmAnimal daisy = ownedCow(1000, 1000);
        collect(entityOf(daisy), EquipmentSlot.HAND);
        ItemStack bundle = onlyCollected();
        bundle.setAmount(1);
        ItemStack held = bundle.clone();
        alice.getInventory().clear();

        open(held, Action.RIGHT_CLICK_AIR);

        assertEquals(0, held.getAmount());
        boolean beef = alice.getInventory().contains(Material.BEEF, 4);
        boolean leather = alice.getInventory().contains(Material.LEATHER, 4);
        assertTrue(beef ^ leather, "one common prize: beef or leather");
    }
}
