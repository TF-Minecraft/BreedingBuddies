package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.AnimalStates;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.ItemUtils;
import net.tfminecraft.breedingbuddies.OwnershipManager;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.UnownedAnimalsManager;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockbukkit.mockbukkit.matcher.sound.SoundReceiverSoundHeardMatcher.hasHeard;
import static org.junit.jupiter.api.Assertions.*;

class TamingListenerTest extends PluginTestBase {
    private TamingListener listener;
    private PlayerMock alice;

    @BeforeEach
    void createListener() {
        listener = new TamingListener(plugin);
        alice = player();
    }

    /** A taming token named in an anvil, which also stores the new name on it. */
    private ItemStack tamingToken(String name, int amount) {
        ItemStack token = mmoItem("TAMING_ITEM");
        token.setAmount(amount);
        ItemMeta meta = token.getItemMeta();
        meta.setDisplayName(name);
        token.setItemMeta(meta);
        ItemUtils.storeDataInItem(token, name, plugin);
        return token;
    }

    private ItemStack shareToken(UUID linkedAnimal) {
        ItemStack token = mmoItem("COOWNERSHIP_ITEM");
        token.setAmount(2);
        if (linkedAnimal != null) {
            ItemUtils.storeDataInItem(token, linkedAnimal.toString(), plugin);
        }
        return token;
    }

    private PlayerInteractEntityEvent useOn(PlayerMock player, Entity target, ItemStack held, EquipmentSlot hand) {
        player.getInventory().setItemInMainHand(held);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, hand);
        listener.onInteract(event);
        return event;
    }

    private PlayerInteractEntityEvent useOn(Entity target, ItemStack held) {
        return useOn(alice, target, held, EquipmentSlot.HAND);
    }

    private static ItemStack held(PlayerMock player) {
        return player.getInventory().getItemInMainHand();
    }

    private void rightClickAir(PlayerMock player, ItemStack held, Action action) {
        listener.onRightClick(new PlayerInteractEvent(player, action, held, null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    @Test
    void aRenamedTokenTamesAFarmAnimal() {
        Cow cow = spawn(Cow.class);
        ItemStack token = tamingToken("Daisy", 2);

        PlayerInteractEntityEvent event = useOn(cow, token);

        assertTrue(event.isCancelled());
        FarmAnimal animal = OwnershipManager.getAnimal(cow.getUniqueId(), alice.getUniqueId());
        assertEquals("Daisy", animal.getName());
        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertEquals("Daisy", cow.getCustomName());
        assertFalse(cow.isCustomNameVisible());
        assertEquals(1, held(alice).getAmount());
        assertEquals("§6You have successfully tamed Daisy and are now its proud owner!", alice.nextMessage());
        assertThat(alice, hasHeard(Sound.BLOCK_AMETHYST_CLUSTER_STEP));
    }

    @Test
    void aTokenThatWasNotRenamedInAnAnvilCannotTame() {
        Cow cow = spawn(Cow.class);
        ItemStack named = mmoItem("TAMING_ITEM");
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Daisy");
        named.setItemMeta(meta);

        useOn(cow, named);
        useOn(cow, mmoItem("TAMING_ITEM"));

        assertFalse(OwnershipManager.hasOwner(cow.getUniqueId()));
        assertEquals(Messages.nameFirst, alice.nextMessage());
        assertEquals(Messages.nameFirst, alice.nextMessage());
        assertEquals(1, named.getAmount());
    }

    @Test
    void tamingASpawnedAnimalKeepsItsStatsAndTakesTheNewName() {
        Cow cow = spawn(Cow.class);
        FarmAnimal spawned = new FarmAnimal(cow.getUniqueId(), "COW", UUID.randomUUID(), cow, 4000, 300);
        spawned.setState(AnimalStates.SPAWNED);
        UnownedAnimalsManager.addUnownedAnimal(spawned);

        useOn(cow, tamingToken("Daisy", 1));

        FarmAnimal tamed = OwnershipManager.getAnimal(cow.getUniqueId(), alice.getUniqueId());
        assertEquals(4000, tamed.getGeneticPoints());
        assertEquals(300, tamed.getFriendshipPoints());
        assertEquals("Daisy", tamed.getName());
        assertEquals(Set.of(alice.getUniqueId()), tamed.getOwnersUuids());
        assertFalse(UnownedAnimalsManager.isUnowned(cow.getUniqueId()));
    }

    @Test
    void tamingABredAnimalKeepsItsGenetics() {
        Cow cow = spawn(Cow.class);
        UnownedAnimalsManager.addUnownedAnimal(new FarmAnimal(cow.getUniqueId(), 777, cow));

        useOn(cow, tamingToken("Daisy", 1));

        FarmAnimal tamed = OwnershipManager.getAnimal(cow.getUniqueId(), alice.getUniqueId());
        assertEquals(777, tamed.getGeneticPoints());
        assertEquals("Daisy", tamed.getName());
        assertFalse(UnownedAnimalsManager.isUnowned(cow.getUniqueId()));
    }

    @Test
    void ownersCanRenameTheirAnimals() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        ItemStack token = tamingToken("Clover", 2);

        useOn(cow, token);

        assertEquals("Clover", daisy.getName());
        assertEquals("Clover", cow.getCustomName());
        assertEquals(1, held(alice).getAmount());
        assertThat(alice, hasHeard(Sound.BLOCK_AMETHYST_CLUSTER_STEP));

        useOn(cow, mmoItem("TAMING_ITEM"));
        assertEquals(Messages.nameFirst, alice.nextMessage());
        assertEquals("Clover", daisy.getName());
    }

    @Test
    void anotherPlayersAnimalCannotBeTamedOrRenamed() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(player(), cow);

        useOn(cow, tamingToken("Mine", 1));

        assertEquals(Messages.notOwner, alice.nextMessage());
        assertEquals("Daisy", daisy.getName());
        assertFalse(OwnershipManager.isOwner(alice.getUniqueId(), cow.getUniqueId()));
    }

    @Test
    void untamedTameableAnimalsMustBeTamedTheVanillaWayFirst() {
        Horse wild = spawn(Horse.class);
        ItemStack token = tamingToken("Storm", 1);

        PlayerInteractEntityEvent event = useOn(wild, token);

        assertTrue(event.isCancelled());
        assertEquals(Messages.tameFirst, alice.nextMessage());
        assertFalse(OwnershipManager.hasOwner(wild.getUniqueId()));

        Horse tamed = spawn(Horse.class);
        tamed.setTamed(true);
        useOn(tamed, token);
        assertTrue(OwnershipManager.isOwner(alice.getUniqueId(), tamed.getUniqueId()));
    }

    @Test
    void tokensOnlyActFromTheMainHandAndOnFarmAnimals() {
        Cow cow = spawn(Cow.class);
        Pig pig = spawn(Pig.class);

        PlayerInteractEntityEvent offHand = useOn(alice, cow, tamingToken("Daisy", 1), EquipmentSlot.OFF_HAND);
        PlayerInteractEntityEvent onPig = useOn(pig, tamingToken("Babe", 1));
        PlayerInteractEntityEvent plainItem = useOn(cow, new ItemStack(Material.WHEAT));

        assertTrue(offHand.isCancelled(), "the token's vanilla use is still blocked");
        assertTrue(onPig.isCancelled());
        assertFalse(plainItem.isCancelled());
        assertFalse(OwnershipManager.hasOwner(cow.getUniqueId()));
        assertFalse(OwnershipManager.hasOwner(pig.getUniqueId()));
        assertNull(alice.nextMessage());
    }

    @Test
    void ownersLinkAShareTokenToTheirAnimal() {
        Cow cow = spawn(Cow.class);
        cow.setCustomName("§aDaisy");
        own(alice, cow);
        ItemStack token = shareToken(null);

        useOn(cow, token);

        ItemStack linked = held(alice);
        assertEquals(cow.getUniqueId().toString(), ItemUtils.getDataFromItem(linked, plugin));
        assertEquals(List.of("§7This token is linked to Daisy the Cow."), linked.getItemMeta().getLore());
        assertThat(alice, hasHeard("block.amethyst_block.step"));

        useOn(cow, linked);
        assertEquals("§6Already linked to §aDaisy.", alice.nextMessage());
    }

    @Test
    void shareTokensNeedAnOwnedAnimalAndItsOwner() {
        Cow wild = spawn(Cow.class);
        Cow theirs = spawn(Cow.class);
        theirs.setCustomName("Bessie");
        own(player(), theirs);
        ItemStack token = shareToken(null);

        useOn(wild, token);
        useOn(theirs, token);

        assertEquals(Messages.ownerFirst, alice.nextMessage());
        assertEquals("§6Only Bessie's owner can share its ownership.", alice.nextMessage());
        assertNull(ItemUtils.getDataFromItem(held(alice), plugin));
    }

    @Test
    void aLinkedTokenGivenToAnotherPlayerMakesThemACoOwner() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        PlayerMock bob = player();
        ItemStack token = shareToken(cow.getUniqueId());

        useOn(bob, token);

        assertEquals(Set.of(alice.getUniqueId(), bob.getUniqueId()), daisy.getOwnersUuids());
        assertTrue(OwnershipManager.isOwner(bob.getUniqueId(), cow.getUniqueId()));
        assertEquals(1, held(alice).getAmount());
        assertThat(bob, hasHeard(Sound.BLOCK_AMETHYST_CLUSTER_STEP));

        useOn(bob, held(alice));
        assertEquals("§6Already an owner of Daisy.", alice.nextMessage());
        assertEquals(1, held(alice).getAmount());
    }

    @Test
    void unlinkedOrStaleTokensCannotBeGivenToPlayers() {
        PlayerMock bob = player();

        useOn(bob, shareToken(null));
        useOn(bob, shareToken(UUID.randomUUID()));

        assertEquals(Messages.useOnOwnedAnimal, alice.nextMessage());
        assertEquals(Messages.invalidAnimalOrNotFound, alice.nextMessage());
        assertTrue(OwnershipManager.getAnimals(bob.getUniqueId()).isEmpty());
    }

    @Test
    void usingALinkedTokenClaimsCoOwnershipForTheHolder() {
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(player(), cow);
        ItemStack token = shareToken(cow.getUniqueId());

        rightClickAir(alice, token, Action.RIGHT_CLICK_AIR);

        assertTrue(daisy.getOwnersUuids().contains(alice.getUniqueId()));
        assertEquals(1, token.getAmount());
        assertThat(alice, hasHeard(Sound.BLOCK_AMETHYST_CLUSTER_STEP));

        rightClickAir(alice, token, Action.RIGHT_CLICK_BLOCK);
        assertEquals("§6Already an owner of Daisy.", alice.nextMessage());
        assertEquals(1, token.getAmount());
    }

    @Test
    void usingAnUnlinkedOrStaleTokenExplainsWhy() {
        rightClickAir(alice, shareToken(null), Action.RIGHT_CLICK_AIR);
        rightClickAir(alice, shareToken(UUID.randomUUID()), Action.RIGHT_CLICK_AIR);

        assertEquals(Messages.useOnOwnedAnimal, alice.nextMessage());
        assertEquals(Messages.invalidAnimalOrNotFound, alice.nextMessage());
    }

    @Test
    void otherClicksWithTokensDoNothing() {
        Cow cow = spawn(Cow.class);
        own(player(), cow);

        rightClickAir(alice, shareToken(cow.getUniqueId()), Action.LEFT_CLICK_AIR);
        rightClickAir(alice, null, Action.RIGHT_CLICK_AIR);
        rightClickAir(alice, tamingToken("Daisy", 1), Action.RIGHT_CLICK_AIR);

        assertFalse(OwnershipManager.isOwner(alice.getUniqueId(), cow.getUniqueId()));
        assertNull(alice.nextMessage());
    }

    @Test
    void tokenLoreNamesTheAnimalAndItsSpecies() {
        SkeletonHorse bones = spawn(SkeletonHorse.class);
        bones.setCustomName("Bones");
        Cow unnamed = spawn(Cow.class);
        ItemStack air = new ItemStack(Material.AIR);
        ItemStack token = mmoItem("COOWNERSHIP_ITEM");

        assertSame(air, listener.changeTokenLore(bones, air));
        assertSame(token, listener.changeTokenLore(null, token));
        assertNull(listener.changeTokenLore(bones, null));
        listener.changeTokenLore(unnamed, token);
        assertNull(token.getItemMeta().getLore());

        listener.changeTokenLore(bones, token);
        assertEquals(List.of("§7This token is linked to Bones the Skeleton Horse."), token.getItemMeta().getLore());
    }

    @Test
    void namingHelpersOnlyNameWithAnAnvilRenamedToken() {
        Cow cow = spawn(Cow.class);
        FarmAnimal animal = new FarmAnimal(cow.getUniqueId(), "Old", new HashSet<>());

        assertFalse(listener.name(cow, new ItemStack(Material.PAPER)));
        assertFalse(listener.name(cow, new ItemStack(Material.PAPER), animal));
        assertTrue(listener.name(cow, tamingToken("New", 1), animal));
        assertEquals("New", animal.getName());
        assertEquals("New", cow.getCustomName());
    }
}
