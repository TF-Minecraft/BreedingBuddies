package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.AnimalInventoryHolder;
import net.tfminecraft.breedingbuddies.AnimalStates;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.MountUtils;
import net.tfminecraft.breedingbuddies.OwnershipManager;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.UnownedAnimalsManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Zombie;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InventoryManagerTest extends PluginTestBase {
    private InventoryManager listener;
    private PlayerMock alice;

    @BeforeEach
    void createListener() {
        listener = new InventoryManager();
        alice = player();
    }

    @AfterEach
    void giveBackAnOpenInventory() {
        // MockBukkit leaves no top inventory after closeInventory; a live server shows the crafting grid.
        for (var player : server.getOnlinePlayers()) {
            player.openInventory(server.createInventory(null, 9));
        }
    }

    private PlayerInteractEntityEvent interact(PlayerMock player, Entity target, ItemStack held, boolean sneaking) {
        player.setSneaking(sneaking);
        player.getInventory().setItemInMainHand(held);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND);
        listener.onPlayerInteractEntity(event);
        return event;
    }

    private static Inventory topOf(PlayerMock player) {
        return player.getOpenInventory().getTopInventory();
    }

    private static boolean showsAnimalMenu(PlayerMock player) {
        // MockBukkit has no top inventory at all until one is opened.
        Inventory top = topOf(player);
        return top != null && top.getHolder() instanceof AnimalInventoryHolder;
    }

    private static String nameAt(Inventory inventory, int slot) {
        return inventory.getItem(slot).getItemMeta().getDisplayName();
    }

    private Horse horse() {
        Horse horse = spawn(Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.225);
        horse.setJumpStrength(1.0);
        return horse;
    }

    @Test
    void sneakingWithAnEmptyHandShowsAnOwnersAnimal() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        daisy.setFriendshipPoints(4000);
        daisy.setGeneticPoints(10_000);

        interact(alice, cow, new ItemStack(Material.AIR), true);

        Inventory menu = topOf(alice);
        assertEquals(27, menu.getSize());
        assertEquals(cow.getUniqueId(), ((AnimalInventoryHolder) menu.getHolder()).getEntityUUID());
        assertEquals("§6Name: Daisy", nameAt(menu, 3));
        assertEquals("§6Next bundle: 00:00", nameAt(menu, 4));
        assertEquals("§6State: HAPPY", nameAt(menu, 5));
        assertEquals(Material.WRITABLE_BOOK, menu.getItem(26).getType());
        assertEquals("§4Remove ownership", nameAt(menu, 26));
        for (int i = 0; i < 5; i++) {
            assertEquals(i < 2 ? Material.RED_DYE : Material.GRAY_DYE, menu.getItem(11 + i).getType());
            assertEquals("§cFriendship: 4000/10000", nameAt(menu, 11 + i));
            assertEquals(Material.SLIME_SPAWN_EGG, menu.getItem(20 + i).getType());
            assertEquals("§eGenetics: 10000/10000", nameAt(menu, 20 + i));
        }
    }

    @Test
    void unownedAnimalsShowTheirMenuWithoutAnOwnershipButton() {
        Cow cow = spawn(Cow.class);
        UnownedAnimalsManager.addUnownedAnimal(new FarmAnimal(cow.getUniqueId(), 0, cow));

        interact(alice, cow, new ItemStack(Material.AIR), true);

        Inventory menu = topOf(alice);
        assertTrue(showsAnimalMenu(alice));
        assertEquals("§6Name: ???", nameAt(menu, 3));
        assertEquals("§6Next bundle: No bundle", nameAt(menu, 4));
        assertNull(menu.getItem(26));
        assertEquals(Material.VEX_SPAWN_EGG, menu.getItem(20).getType());
    }

    @Test
    void otherPlayersAnimalsAreShownOnlyToStaff() {
        Cow cow = spawn(Cow.class);
        own(player(), cow);
        PlayerMock staff = player();
        staff.addAttachment(plugin, "breedingbuddies.use", true);

        interact(alice, cow, new ItemStack(Material.AIR), true);
        interact(staff, cow, new ItemStack(Material.AIR), true);

        assertFalse(showsAnimalMenu(alice));
        assertTrue(showsAnimalMenu(staff));
        assertNull(topOf(staff).getItem(26), "staff are not offered someone else's ownership");
    }

    @Test
    void ordinaryClicksAndUntrackedAnimalsOpenNothing() {
        Cow owned = spawn(Cow.class);
        own(alice, owned);
        Cow wild = spawn(Cow.class);
        Zombie zombie = spawn(Zombie.class);

        interact(alice, owned, new ItemStack(Material.AIR), false);
        interact(alice, wild, new ItemStack(Material.AIR), true);
        interact(alice, zombie, new ItemStack(Material.CARROT_ON_A_STICK), true);
        interact(alice, owned, new ItemStack(Material.WHEAT), true);

        assertFalse(showsAnimalMenu(alice));
    }

    @Test
    void sneakingOnAMountLeavesTheVanillaHorseInventoryAlone() {
        Horse horse = horse();
        own(alice, horse);

        PlayerInteractEntityEvent event = interact(alice, horse, new ItemStack(Material.AIR), true);

        assertFalse(event.isCancelled());
        assertFalse(showsAnimalMenu(alice));
    }

    @Test
    void theMountStatsItemShowsAnyUntrackedMountsStats() {
        Horse horse = horse();
        MountUtils.setNeuteredFlag(horse, true);

        PlayerInteractEntityEvent event = interact(alice, horse, new ItemStack(Material.CARROT_ON_A_STICK), false);

        assertTrue(event.isCancelled());
        Inventory menu = topOf(alice);
        assertEquals(36, menu.getSize());
        assertEquals("§6Name: ???", nameAt(menu, 3));
        assertEquals("§6State: UNOWNED", nameAt(menu, 5));
        assertEquals("§6Neutered: true", nameAt(menu, 8));
        assertEquals("§dHealth: 15.00", nameAt(menu, 30));
        assertEquals(String.format("§bSpeed: %.2f", 0.225 * 42.16), nameAt(menu, 31));
        assertEquals(String.format("§aJump: %.2f", MountUtils.jumpStrengthToBlockHeight(1.0)), nameAt(menu, 32));
        assertNull(menu.getItem(35));
        assertFalse(UnownedAnimalsManager.isUnowned(horse.getUniqueId()), "looking does not start tracking");
    }

    @Test
    void ownersSeeTheirMountsRecordAndOwnershipButton() {
        Horse horse = horse();
        FarmAnimal storm = own(alice, horse);
        storm.setFriendshipPoints(10_000);

        interact(alice, horse, new ItemStack(Material.CARROT_ON_A_STICK), false);

        Inventory menu = topOf(alice);
        assertEquals("§6Name: Daisy", nameAt(menu, 3));
        assertEquals("§6Neutered: false", nameAt(menu, 8));
        assertEquals(Material.WRITABLE_BOOK, menu.getItem(35).getType());
        for (int i = 0; i < 5; i++) {
            assertEquals(Material.RED_DYE, menu.getItem(11 + i).getType());
        }
    }

    @Test
    void otherPlayersMountsAreShownOnlyToStaff() {
        Horse horse = horse();
        own(player(), horse);

        PlayerInteractEntityEvent event = interact(alice, horse, new ItemStack(Material.CARROT_ON_A_STICK), false);

        assertFalse(event.isCancelled());
        assertFalse(showsAnimalMenu(alice));
    }

    @Test
    void theMountStatsItemShowsTheAnimalMenuForOtherFarmAnimals() {
        Cow owned = spawn(Cow.class);
        own(alice, owned);
        Cow unowned = spawn(Cow.class);
        UnownedAnimalsManager.addUnownedAnimal(new FarmAnimal(unowned.getUniqueId(), unowned));
        Cow theirs = spawn(Cow.class);
        own(player(), theirs);

        interact(alice, owned, new ItemStack(Material.CARROT_ON_A_STICK), false);
        assertEquals(27, topOf(alice).getSize());
        assertEquals("§6Name: Daisy", nameAt(topOf(alice), 3));

        interact(alice, unowned, new ItemStack(Material.CARROT_ON_A_STICK), false);
        assertEquals("§6Name: ???", nameAt(topOf(alice), 3));

        alice.openInventory(server.createInventory(null, 9));
        interact(alice, theirs, new ItemStack(Material.CARROT_ON_A_STICK), false);
        assertFalse(showsAnimalMenu(alice));
    }

    @Test
    void inspectingAnOwnedAnimalCatchesUpOnMissedDays() throws Exception {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        daisy.setFed(true);
        setLastDayChange(1);

        interact(alice, cow, new ItemStack(Material.AIR), true);
        awaitDayChange();

        assertFalse(daisy.getFed());
    }

    private InventoryClickEvent click(PlayerMock player, int slot) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(event);
        return event;
    }

    @Test
    void theAnimalMenuCannotBeRearranged() {
        Cow cow = spawn(Cow.class);
        own(alice, cow);
        interact(alice, cow, new ItemStack(Material.AIR), true);

        InventoryClickEvent onName = click(alice, 3);
        InventoryClickEvent onEmpty = click(alice, 0);

        assertTrue(onName.isCancelled());
        assertTrue(onEmpty.isCancelled());
        assertTrue(OwnershipManager.isOwner(alice.getUniqueId(), cow.getUniqueId()));
    }

    @Test
    void theLastOwnerGivingUpAnAnimalLeavesItUnowned() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        interact(alice, cow, new ItemStack(Material.AIR), true);

        click(alice, 26);

        assertFalse(OwnershipManager.isOwner(alice.getUniqueId(), cow.getUniqueId()));
        assertEquals(AnimalStates.UNOWNED, daisy.getState());
        assertSame(daisy, UnownedAnimalsManager.getAnimal(cow.getUniqueId()));
        assertEquals("§6You are no longer the owner of Daisy.", alice.nextMessage());
        assertFalse(showsAnimalMenu(alice));
    }

    @Test
    void aCoOwnerGivingUpLeavesTheOthersInCharge() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        PlayerMock bob = player();
        OwnershipManager.registerOwnership(bob.getUniqueId(), daisy);
        interact(alice, cow, new ItemStack(Material.AIR), true);

        click(alice, 26);

        assertEquals(Set.of(bob.getUniqueId()), daisy.getOwnersUuids());
        assertEquals(AnimalStates.HAPPY, daisy.getState());
        assertFalse(UnownedAnimalsManager.isUnowned(cow.getUniqueId()));
    }

    @Test
    void theRemoveButtonDoesNothingForANonOwner() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(player(), cow);
        Inventory menu = new AnimalInventoryHolder(cow.getUniqueId(), 27, "Animal Info").getInventory();
        menu.setItem(26, new ItemStack(Material.WRITABLE_BOOK));
        alice.openInventory(menu);

        assertTrue(click(alice, 26).isCancelled());
        assertEquals(1, daisy.getOwnersUuids().size());
        assertNull(alice.nextMessage());
    }

    @Test
    void otherInventoriesAreLeftAlone() {
        Inventory chest = server.createInventory(null, 27);
        chest.setItem(0, new ItemStack(Material.WRITABLE_BOOK));
        alice.openInventory(chest);

        assertFalse(click(alice, 0).isCancelled());
    }
}
