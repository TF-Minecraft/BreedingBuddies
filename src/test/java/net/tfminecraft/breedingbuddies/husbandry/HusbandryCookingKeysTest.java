package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import net.tfminecraft.breedingbuddies.BreedingBuddies;

class HusbandryCookingKeysTest {
    private ServerMock server;
    private BreedingBuddies previous;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        previous = BreedingBuddies.plugin;
        BreedingBuddies.plugin = mock(BreedingBuddies.class);
        when(BreedingBuddies.plugin.getName()).thenReturn("BreedingBuddies");
        when(BreedingBuddies.plugin.namespace()).thenReturn("breedingbuddies");
    }

    @AfterEach void tearDown() {
        MockBukkit.unmock();
        BreedingBuddies.plugin = previous;
    }

    @Test void animalsCookingManagedStayManagedAndNewStampsUseBreedingBuddiesKeys() {
        WorldMock world = server.addSimpleWorld("farm");
        Cow fromCooking = world.spawn(new Location(world, 0, 65, 0), Cow.class);
        fromCooking.getPersistentDataContainer().set(cooking("managed"), PersistentDataType.BYTE, (byte) 1);
        assertTrue(HusbandryEntities.isManaged(fromCooking));

        Cow fresh = world.spawn(new Location(world, 2, 65, 0), Cow.class);
        assertFalse(HusbandryEntities.isManaged(fresh));
        HusbandryEntities.stampManaged(fresh);
        assertTrue(HusbandryEntities.isManaged(fresh));
        assertNull(fresh.getPersistentDataContainer().get(cooking("managed"), PersistentDataType.BYTE));
    }

    @Test void tokensNamedOrLinkedByCookingStillWorkAndOurOwnValuesWin() {
        ItemStack tame = token("tame_name", "Bessie");
        assertEquals("Bessie", HusbandryItems.tameName(tame));
        HusbandryItems.setTameName(tame, "Daisy");
        assertEquals("Daisy", HusbandryItems.tameName(tame));

        ItemStack share = token("linked_animal", "0000-cooking");
        assertEquals("0000-cooking", HusbandryItems.linkedAnimal(share));
        HusbandryItems.setLinkedAnimal(share, "1111-breedingbuddies", null);
        assertEquals("1111-breedingbuddies", HusbandryItems.linkedAnimal(share));
    }

    private static ItemStack token(String key, String value) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(cooking(key), PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        return stack;
    }

    private static NamespacedKey cooking(String key) {
        return new NamespacedKey("cooking", key);
    }
}
