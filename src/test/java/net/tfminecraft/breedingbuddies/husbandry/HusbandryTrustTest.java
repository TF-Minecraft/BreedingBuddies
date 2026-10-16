package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRuntimeCoverageTest.Farm;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryTrust.Gesture;

class HusbandryTrustTest {
    private static final long HOUR = 3_600_000L;
    @TempDir Path directory;
    private Farm f;

    @BeforeEach void open() throws Exception {
        f = new Farm(directory);
        f.config.set("trust.feed", 10); f.config.set("trust.glove", 12); f.config.set("trust.pet", 5); f.config.set("trust.pet-cooldown", "20h");
        f.reload();
    }

    @AfterEach void close() throws Exception { f.close(); }

    @Test void eachGestureAddsItsTrustUpToTheMaximumAndPetsWaitForTheCooldown() {
        HusbandryAnimal animal = f.record(f.spawn(Cow.class));
        assertEquals(0, HusbandryTrust.reward(null, Gesture.FEED, 0));
        assertEquals(0, HusbandryTrust.reward(animal, null, 0));
        assertEquals(10, HusbandryTrust.reward(animal, Gesture.FEED, 0));
        assertEquals(12, HusbandryTrust.reward(animal, Gesture.GLOVE, 0));
        assertEquals(5, HusbandryTrust.reward(animal, Gesture.PET, 0));
        assertEquals(0, HusbandryTrust.reward(animal, Gesture.PET, 19 * HOUR), "Still within the pet cooldown");
        assertEquals(5, HusbandryTrust.reward(animal, Gesture.PET, 20 * HOUR));
        assertEquals(32, animal.care());
        animal.setCare(HusbandryConfig.careMax() - 3);
        assertEquals(3, HusbandryTrust.reward(animal, Gesture.FEED, 0));
        assertEquals(HusbandryConfig.careMax(), animal.care());
    }

    @Test void heartsRoundTrustToFiveAndShowAnyTrustAsAtLeastOne() {
        int max = HusbandryConfig.careMax();
        assertEquals(0, HusbandryTrust.filledHearts(0));
        assertEquals(1, HusbandryTrust.filledHearts(1));
        assertEquals(3, HusbandryTrust.filledHearts(max / 2));
        assertEquals(5, HusbandryTrust.filledHearts(max));
        assertEquals("❤❤❤❤❤", plain(HusbandryTrust.hearts(max / 2)));
    }

    @Test void owningPlayersPetWithAnEmptyStandingHandOncePerCooldown() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow);
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.neighbour, cow));
        assertEquals(0, animal.care(), "Only owners earn trust");
        f.own(animal, f.player);
        f.player.setSneaking(true);
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, cow));
        assertEquals(0, animal.care(), "Sneaking opens the menu instead");
        f.player.setSneaking(false);

        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, cow));

        assertEquals(5, animal.care());
        assertEquals(5, f.repository.getAnimal(animal.uuid()).orElseThrow().care());
        assertNotNull(f.repository.getAnimal(animal.uuid()).orElseThrow().lastPetAt());
        assertTrue(plain(f.player.nextActionBar()).startsWith("Cow "));
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, cow));
        assertEquals(5, animal.care(), "A second pet the same day adds nothing");
        assertNotNull(f.player.nextActionBar(), "but still shows the hearts");
    }

    @Test void feedingAndBrushingBuildTrustOnlyForOwners() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        animal.setHungrySince(1L); animal.setDirtySince(1L);
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT, 2));
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, cow));
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.LEATHER));
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, cow));
        assertEquals(22, animal.care());
        assertNull(animal.hungrySince()); assertNull(animal.dirtySince());

        Cow other = f.spawn(Cow.class); HusbandryAnimal tended = f.record(other); f.own(tended, f.player);
        tended.setHungrySince(1L); f.neighbour.setOp(true);
        f.neighbour.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT));
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.neighbour, other));
        assertNull(tended.hungrySince(), "Staff can still feed it");
        assertEquals(0, tended.care(), "but its trust is for its owners");
    }

    @Test void mountsArePettedByRidingNotByClicking() {
        registerMountListener();
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse); f.own(animal, f.player);
        new HusbandryCareListener().onInteract(new PlayerInteractEntityEvent(f.player, horse));
        assertEquals(0, animal.care());

        EntityMountEvent ride = new EntityMountEvent(f.player, horse);
        f.server.getPluginManager().callEvent(ride);
        assertFalse(ride.isCancelled());
        assertEquals(5, animal.care());
        assertEquals(5, f.repository.getAnimal(animal.uuid()).orElseThrow().care());

        f.server.getPluginManager().callEvent(new EntityMountEvent(f.player, horse));
        assertEquals(5, animal.care(), "One ride a day counts");
    }

    @Test void anAlreadyCancelledRideDoesNotEarnTrustOrConsumeThePetCooldown() {
        registerMountListener();
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse); f.own(animal, f.player);
        EntityMountEvent ride = new EntityMountEvent(f.player, horse);
        ride.setCancelled(true);

        f.server.getPluginManager().callEvent(ride);

        assertNoRideReward(animal);
    }

    @Test void aRideCancelledAfterOwnershipChecksDoesNotEarnTrustOrConsumeThePetCooldown() {
        registerMountListener();
        f.server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST)
            public void cancelRide(EntityMountEvent event) { event.setCancelled(true); }
        }, MockBukkit.createMockPlugin());
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse); f.own(animal, f.player);
        EntityMountEvent ride = new EntityMountEvent(f.player, horse);

        f.server.getPluginManager().callEvent(ride);

        assertTrue(ride.isCancelled());
        assertNoRideReward(animal);
    }

    @Test void onlyOwnersRidingMountsEarnTrust() {
        registerMountListener();
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        f.server.getPluginManager().callEvent(new EntityMountEvent(f.player, cow));
        assertNoRideReward(animal);
        Horse horse = f.spawn(Horse.class); HusbandryAnimal mount = f.record(horse);
        f.server.getPluginManager().callEvent(new EntityMountEvent(f.player, horse));
        assertNoRideReward(mount);
        f.own(mount, f.player);
        f.neighbour.setOp(true);
        f.server.getPluginManager().callEvent(new EntityMountEvent(f.neighbour, horse));
        f.server.getPluginManager().callEvent(new EntityMountEvent(cow, horse));
        assertNoRideReward(mount);
    }

    private void registerMountListener() {
        f.server.getPluginManager().registerEvents(new HusbandryMountListener(), MockBukkit.createMockPlugin());
    }

    private void assertNoRideReward(HusbandryAnimal animal) {
        assertEquals(0, animal.care());
        assertNull(animal.lastPetAt());
        HusbandryAnimal saved = f.repository.getAnimal(animal.uuid()).orElseThrow();
        assertEquals(0, saved.care());
        assertNull(saved.lastPetAt());
    }

    @Test void neglectWithNoTrustLossConfiguredKeepsTrust() throws Exception {
        f.config.set("care.down.amount", 0); f.reload();
        HusbandryAnimal animal = HusbandryRuntimeCoverageTest.simulationAnimal(); animal.setCare(9); animal.setHungrySince(0L);
        HusbandrySimulator.tickLoaded(animal, 100 * HOUR, new Random(1));
        assertEquals(9, animal.care());
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
