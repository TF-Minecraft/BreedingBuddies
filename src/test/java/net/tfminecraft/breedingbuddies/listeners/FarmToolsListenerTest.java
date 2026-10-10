package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.ChunkManager;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.ItemUtils;
import net.tfminecraft.breedingbuddies.OwnershipManager;
import net.tfminecraft.breedingbuddies.PluginData;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.UnownedAnimalsManager;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockbukkit.mockbukkit.matcher.sound.SoundReceiverSoundHeardMatcher.hasHeard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Farm report, stable chunk claiming, anvil renaming and animal deaths. */
class FarmToolsListenerTest extends PluginTestBase {
    private PlayerMock alice;

    @BeforeEach
    void addPlayer() {
        alice = player();
    }

    private PlayerInteractEvent click(Action action, ItemStack item) {
        return new PlayerInteractEvent(alice, action, item, null, BlockFace.SELF, EquipmentSlot.HAND);
    }

    /** With no clicked block isCancelled() is always true, so check the item use instead. */
    private static boolean itemUseDenied(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }

    @Test
    void theFarmReportSummarisesTheOwnersAnimals() {
        FarmAnimal fed = own(alice, spawn(Cow.class));
        fed.setFed(true);
        fed.setCared(true);
        FarmAnimal outside = own(alice, spawn(Cow.class));
        outside.setSleptInStable(false);
        outside.setSpaceNeeded(true);
        outside.setWaterNeeded(true);
        outside.setFed(true);

        new FarmReport().onRightClick(click(Action.RIGHT_CLICK_AIR, mmoItem("FARMREPORT_ITEM")));

        assertEquals("§6§nFarm Report:", alice.nextMessage());
        assertEquals("§e-§oAnimals fed:§r 2.", alice.nextMessage());
        assertEquals("§e-§oAnimals taken care of:§r 1.", alice.nextMessage());
        assertEquals("§e-§oTotal animals owned:§r 2.", alice.nextMessage());
        assertEquals(Messages.animalsOut, alice.nextMessage());
        assertEquals(Messages.spaceNeeded, alice.nextMessage());
        assertEquals(Messages.waterNeeded, alice.nextMessage());
        assertNull(alice.nextMessage());
        assertThat(alice, hasHeard(Sound.ITEM_BOOK_PAGE_TURN));
    }

    @Test
    void aHealthyFarmReportsNoWarnings() {
        own(alice, spawn(Cow.class));

        new FarmReport().onRightClick(click(Action.RIGHT_CLICK_BLOCK, mmoItem("FARMREPORT_ITEM")));

        assertEquals("§6§nFarm Report:", alice.nextMessage());
        assertEquals("§e-§oAnimals fed:§r 0.", alice.nextMessage());
        assertEquals("§e-§oAnimals taken care of:§r 0.", alice.nextMessage());
        assertEquals("§e-§oTotal animals owned:§r 1.", alice.nextMessage());
        assertNull(alice.nextMessage());
    }

    @Test
    void theFarmReportFirstCatchesUpOnMissedDays() {
        FarmAnimal daisy = own(alice, spawn(Cow.class));
        daisy.setFed(true);
        setLastDayChange(1);

        new FarmReport().onRightClick(click(Action.RIGHT_CLICK_AIR, mmoItem("FARMREPORT_ITEM")));

        alice.nextMessage();
        assertEquals("§e-§oAnimals fed:§r 0.", alice.nextMessage());
    }

    @Test
    void otherItemsAndClicksDoNotProduceAReport() {
        FarmReport report = new FarmReport();

        report.onRightClick(click(Action.LEFT_CLICK_AIR, mmoItem("FARMREPORT_ITEM")));
        report.onRightClick(click(Action.RIGHT_CLICK_AIR, null));
        report.onRightClick(click(Action.RIGHT_CLICK_AIR, new ItemStack(Material.BOOK)));

        assertNull(alice.nextMessage());
    }

    @Test
    void theStableItemClaimsTheChunkUnderThePlayerOnce() {
        ChunkCreation listener = new ChunkCreation();
        ItemStack fences = mmoItem("STABLECHUNK_ITEM");
        fences.setAmount(2);

        PlayerInteractEvent first = click(Action.RIGHT_CLICK_BLOCK, fences);
        listener.onRightClick(first);

        assertTrue(itemUseDenied(first));
        assertTrue(ChunkManager.isStableChunk(alice.getLocation().getChunk()));
        assertEquals(1, fences.getAmount());
        assertEquals(Messages.stableChunk, alice.nextMessage());
        assertThat(alice, hasHeard(Sound.BLOCK_HANGING_SIGN_PLACE));

        PlayerInteractEvent again = click(Action.RIGHT_CLICK_AIR, fences);
        listener.onRightClick(again);

        assertTrue(itemUseDenied(again));
        assertEquals(1, fences.getAmount());
        assertEquals(Messages.alreadyStableChunk, alice.nextMessage());
        assertEquals(1, PluginData.getChunkAreas().size());
    }

    @Test
    void otherItemsAndClicksClaimNothing() {
        ChunkCreation listener = new ChunkCreation();
        PlayerInteractEvent leftClick = click(Action.LEFT_CLICK_BLOCK, mmoItem("STABLECHUNK_ITEM"));
        PlayerInteractEvent fence = click(Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.OAK_FENCE));
        PlayerInteractEvent empty = click(Action.RIGHT_CLICK_BLOCK, null);

        listener.onRightClick(leftClick);
        listener.onRightClick(fence);
        listener.onRightClick(empty);

        assertFalse(itemUseDenied(fence));
        assertTrue(PluginData.getChunkAreas().isEmpty());
    }

    private PrepareAnvilEvent anvil(ItemStack result, String renameText) {
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        AnvilInventory inventory = mock(AnvilInventory.class);
        when(event.getResult()).thenReturn(result);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getRenameText()).thenReturn(renameText);
        return event;
    }

    @Test
    void renamingATamingTokenInAnAnvilRecordsTheName() {
        AnvilRenameListener listener = new AnvilRenameListener(plugin);
        ItemStack token = mmoItem("TAMING_ITEM");
        ItemStack paper = new ItemStack(Material.PAPER);

        listener.onAnvilRename(anvil(token, "Daisy"));
        listener.onAnvilRename(anvil(paper, "Daisy"));
        listener.onAnvilRename(anvil(null, "Daisy"));

        assertEquals("Daisy", ItemUtils.getDataFromItem(token, plugin));
        assertNull(ItemUtils.getDataFromItem(paper, plugin));

        ItemStack unnamed = mmoItem("TAMING_ITEM");
        listener.onAnvilRename(anvil(unnamed, null));
        assertNull(ItemUtils.getDataFromItem(unnamed, plugin));
    }

    @Test
    void deadAnimalsAreForgotten() {
        Cow owned = spawn(Cow.class);
        own(alice, owned);
        Cow unowned = spawn(Cow.class);
        UnownedAnimalsManager.addUnownedAnimal(new FarmAnimal(unowned.getUniqueId(), unowned));
        AnimalDeathListener listener = new AnimalDeathListener();

        listener.onAnimalDeath(death(owned));
        listener.onAnimalDeath(death(unowned));

        assertFalse(OwnershipManager.hasOwner(owned.getUniqueId()));
        assertTrue(OwnershipManager.getAnimals(alice.getUniqueId()).isEmpty());
        assertFalse(UnownedAnimalsManager.isUnowned(unowned.getUniqueId()));
    }

    @Test
    void otherDeathsAreIgnored() {
        Zombie zombie = spawn(Zombie.class);
        Cow cow = spawn(Cow.class);
        FarmAnimal daisy = own(alice, cow);
        // A record sharing the zombie's ID shows the zombie's death is not looked up.
        OwnershipManager.registerOwnership(alice.getUniqueId(), new FarmAnimal(zombie.getUniqueId(), "Odd", new java.util.HashSet<>()));

        new AnimalDeathListener().onAnimalDeath(death(zombie));

        assertEquals(2, OwnershipManager.getAnimals(alice.getUniqueId()).size());
        assertTrue(OwnershipManager.getAnimals(alice.getUniqueId()).contains(daisy));
    }

    private static EntityDeathEvent death(LivingEntity entity) {
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);
        return event;
    }
}
