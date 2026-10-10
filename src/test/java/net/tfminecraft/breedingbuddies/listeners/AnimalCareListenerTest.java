package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class AnimalCareListenerTest extends PluginTestBase {
    private AnimalCareListener listener;
    private PlayerMock alice;
    private Cow cow;

    @BeforeEach
    void setUpFarm() {
        listener = new AnimalCareListener();
        alice = player();
        cow = spawn(Cow.class);
    }

    private PlayerInteractEntityEvent use(PlayerMock player, ItemStack held, EquipmentSlot hand) {
        player.getInventory().setItemInMainHand(held);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, cow, hand);
        listener.onInteract(event);
        return event;
    }

    private static ItemStack stack(String id, int amount) {
        ItemStack item = mmoItem(id);
        item.setAmount(amount);
        return item;
    }

    @Test
    void feedingOnceADayBuildsFriendshipAndUsesTheFeed() {
        FarmAnimal daisy = own(alice, cow);

        PlayerInteractEntityEvent event = use(alice, stack("UNIVERSAL_FEED", 3), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertTrue(daisy.getFed());
        assertEquals(200, daisy.getFriendshipPoints());
        assertEquals(2, alice.getInventory().getItemInMainHand().getAmount());

        use(alice, alice.getInventory().getItemInMainHand(), EquipmentSlot.HAND);
        assertEquals("§6Daisy has already been fed.", alice.nextMessage());
        assertEquals(200, daisy.getFriendshipPoints());
        assertEquals(2, alice.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void caringOnceADayBuildsFriendshipAndKeepsTheItem() {
        FarmAnimal daisy = own(alice, cow);

        PlayerInteractEntityEvent event = use(alice, stack("CARING_ITEM", 1), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertTrue(daisy.getCared());
        assertEquals(200, daisy.getFriendshipPoints());
        assertEquals(1, alice.getInventory().getItemInMainHand().getAmount());

        use(alice, stack("CARING_ITEM", 1), EquipmentSlot.HAND);
        assertEquals("§6Daisy has already been taken care of.", alice.nextMessage());
        assertEquals(200, daisy.getFriendshipPoints());
    }

    @Test
    void careFirstCatchesUpOnMissedDays() throws Exception {
        FarmAnimal daisy = own(alice, cow);
        daisy.setFriendshipPoints(1000);
        daisy.setFed(true);
        setLastDayChange(1);

        use(alice, stack("UNIVERSAL_FEED", 1), EquipmentSlot.HAND);
        awaitDayChange();

        assertTrue(daisy.getFed(), "fed for the new day");
        assertEquals(1000 - 100 + 200, daisy.getFriendshipPoints());
    }

    @Test
    void otherItemsAndTheOffHandDoNothing() {
        FarmAnimal daisy = own(alice, cow);

        PlayerInteractEntityEvent wheat = use(alice, new ItemStack(Material.WHEAT), EquipmentSlot.HAND);
        PlayerInteractEntityEvent offHand = use(alice, stack("UNIVERSAL_FEED", 1), EquipmentSlot.OFF_HAND);

        assertFalse(wheat.isCancelled());
        assertFalse(offHand.isCancelled());
        assertFalse(daisy.getFed());
        assertEquals(0, daisy.getFriendshipPoints());
    }

    @Test
    void playersCannotHandleSomeoneElsesAnimalButStaffCan() {
        FarmAnimal daisy = own(player(), cow);
        PlayerMock staff = player();
        staff.addAttachment(plugin, "breedingbuddies.use", true);

        PlayerInteractEntityEvent stranger = use(alice, new ItemStack(Material.LEAD), EquipmentSlot.HAND);
        PlayerInteractEntityEvent byStaff = use(staff, new ItemStack(Material.LEAD), EquipmentSlot.HAND);

        assertTrue(stranger.isCancelled());
        assertEquals(Messages.notOwner, alice.nextMessage());
        assertFalse(byStaff.isCancelled());
        assertNull(staff.nextMessage());
        assertFalse(daisy.getFed());
    }

    @Test
    void wildAnimalsAreLeftToVanilla() {
        PlayerInteractEntityEvent event = use(alice, stack("UNIVERSAL_FEED", 1), EquipmentSlot.HAND);

        assertFalse(event.isCancelled());
        assertNull(alice.nextMessage());
        assertEquals(1, alice.getInventory().getItemInMainHand().getAmount());
    }
}
