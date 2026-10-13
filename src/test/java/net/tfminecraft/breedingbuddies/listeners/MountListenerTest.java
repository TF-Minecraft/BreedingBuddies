package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.MountUtils;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockbukkit.mockbukkit.matcher.sound.SoundReceiverSoundHeardMatcher.hasHeard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Riding, neutering and the optional stat nerf for mounts. */
class MountListenerTest extends PluginTestBase {
    private MountListener mounts;
    private PlayerMock alice;

    @BeforeEach
    void createListener() {
        mounts = new MountListener();
        alice = player();
    }

    private EntityMountEvent mount(Entity rider, Entity mount) {
        EntityMountEvent event = new EntityMountEvent(rider, mount);
        mounts.onMount(event);
        mounts.onMountFix(event);
        return event;
    }

    private PlayerInteractEntityEvent interact(PlayerMock player, Entity target, ItemStack held, EquipmentSlot hand) {
        player.getInventory().setItemInMainHand(held);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, hand);
        mounts.onInteract(event);
        mounts.onInteractFix(event);
        return event;
    }

    private PlayerMock staff() {
        PlayerMock staff = player();
        staff.addAttachment(plugin, "breedingbuddies.use", true);
        return staff;
    }

    private Horse foal() {
        Horse horse = spawn(Horse.class);
        horse.setBaby();
        return horse;
    }

    @Test
    void onlyOwnersMayRideAnOwnedAnimal() {
        Horse horse = spawn(Horse.class);
        own(alice, horse);
        PlayerMock bob = player();

        EntityMountEvent byStranger = mount(bob, horse);
        EntityMountEvent byOwner = mount(alice, horse);
        EntityMountEvent onWild = mount(bob, spawn(Horse.class));

        assertTrue(byStranger.isCancelled());
        assertThat(bob, hasHeard("entity.horse.ambient"));
        assertFalse(byOwner.isCancelled());
        assertFalse(onWild.isCancelled());
    }

    @Test
    void ridingByMobsOrOnNonAnimalsIsIgnored() {
        Horse horse = spawn(Horse.class);
        FarmAnimal record = own(alice, horse);
        record.setStableNeeded(true);

        EntityMountEvent mobRider = mount(spawn(Zombie.class), horse);
        EntityMountEvent boat = mount(alice, mock(Entity.class));

        assertFalse(mobRider.isCancelled());
        assertFalse(boat.isCancelled());
        assertTrue(record.isStableNeeded(), "only the owner riding updates the record");
    }

    @Test
    void ridingOrTouchingAnOwnedMountMarksItAsNotNeedingAStable() {
        Horse ridden = spawn(Horse.class);
        FarmAnimal riddenRecord = own(alice, ridden);
        riddenRecord.setStableNeeded(true);
        Horse touched = spawn(Horse.class);
        FarmAnimal touchedRecord = own(alice, touched);
        touchedRecord.setStableNeeded(true);
        Cow cow = spawn(Cow.class);
        FarmAnimal cowRecord = own(alice, cow);

        mount(alice, ridden);
        interact(alice, touched, new ItemStack(Material.AIR), EquipmentSlot.HAND);
        interact(alice, cow, new ItemStack(Material.AIR), EquipmentSlot.HAND);
        interact(alice, spawn(Horse.class), new ItemStack(Material.AIR), EquipmentSlot.HAND);

        assertFalse(riddenRecord.isStableNeeded());
        assertFalse(touchedRecord.isStableNeeded());
        assertTrue(cowRecord.isStableNeeded());
    }

    @Test
    void ownersCanNeuterTheirFoals() {
        Horse foal = foal();
        own(alice, foal);

        PlayerInteractEntityEvent event = interact(alice, foal, new ItemStack(Material.SHEARS), EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertTrue(MountUtils.isNeutered(foal));
        assertThat(alice, hasHeard(Sound.ENTITY_HORSE_DEATH));
    }

    @Test
    void adultsCanOnlyBeNeuteredByStaffWhoOwnThem() {
        Horse adult = spawn(Horse.class);
        own(alice, adult);
        PlayerMock staff = staff();
        Horse staffHorse = spawn(Horse.class);
        own(staff, staffHorse);

        PlayerInteractEntityEvent byOwner = interact(alice, adult, new ItemStack(Material.SHEARS), EquipmentSlot.HAND);
        PlayerInteractEntityEvent byStaffNotOwner = interact(staff, adult, new ItemStack(Material.SHEARS), EquipmentSlot.HAND);
        PlayerInteractEntityEvent byStaffOwner = interact(staff, staffHorse, new ItemStack(Material.SHEARS), EquipmentSlot.HAND);

        assertFalse(byOwner.isCancelled());
        assertFalse(byStaffNotOwner.isCancelled());
        assertFalse(MountUtils.isNeutered(adult));
        assertTrue(byStaffOwner.isCancelled());
        assertTrue(MountUtils.isNeutered(staffHorse));
    }

    @Test
    void neuteringNeedsShearsTheMainHandAnOwnerAndAnIntactMount() {
        Horse foal = foal();
        own(alice, foal);
        Horse theirs = foal();
        own(player(), theirs);
        Horse done = foal();
        own(alice, done);
        MountUtils.setNeuteredFlag(done, true);
        Cow calf = spawn(Cow.class);
        own(alice, calf);

        assertFalse(interact(alice, foal, new ItemStack(Material.WHEAT), EquipmentSlot.HAND).isCancelled());
        assertFalse(interact(alice, foal, new ItemStack(Material.SHEARS), EquipmentSlot.OFF_HAND).isCancelled());
        assertTrue(interact(alice, theirs, new ItemStack(Material.SHEARS), EquipmentSlot.HAND).isCancelled());
        assertFalse(interact(alice, done, new ItemStack(Material.SHEARS), EquipmentSlot.HAND).isCancelled());
        assertFalse(interact(alice, calf, new ItemStack(Material.SHEARS), EquipmentSlot.HAND).isCancelled());

        assertFalse(MountUtils.isNeutered(foal));
        assertFalse(MountUtils.isNeutered(theirs));
    }

    // Nerfing

    private final NerfMountListener nerf = new NerfMountListener();

    private Horse horse(double health, double speed, double jump) {
        Horse horse = spawn(Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        horse.setJumpStrength(jump);
        return horse;
    }

    private static void assertStats(Horse horse, double health, double speed, double jump) {
        assertEquals(health, horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue(), 1e-9);
        assertEquals(speed, horse.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue(), 1e-9);
        assertEquals(jump, horse.getJumpStrength(), 1e-9);
    }

    @Test
    void naturalAndEggSpawnedMountsAreNerfedOnceWhenEnabled() {
        Numbers.nerfMountStats = true;
        Horse natural = horse(20, 0.2, 0.7);
        Horse egg = horse(20, 0.2, 0.7);

        nerf.onHorseSpawn(new CreatureSpawnEvent(natural, CreatureSpawnEvent.SpawnReason.NATURAL));
        nerf.onHorseSpawn(new CreatureSpawnEvent(egg, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG));
        nerf.onHorseSpawn(new CreatureSpawnEvent(natural, CreatureSpawnEvent.SpawnReason.NATURAL));

        assertStats(natural, 10, 0.1, 0.35);
        assertStats(egg, 10, 0.1, 0.35);
        assertTrue(nerf.isNerfed(natural));
    }

    @Test
    void commandSpawnedMountsAreOnlyMarkedAndBredOnesUntouched() {
        Numbers.nerfMountStats = true;
        Horse commanded = horse(20, 0.2, 0.7);
        Horse bred = horse(20, 0.2, 0.7);

        nerf.onHorseSpawn(new CreatureSpawnEvent(commanded, CreatureSpawnEvent.SpawnReason.COMMAND));
        nerf.onHorseSpawn(new CreatureSpawnEvent(bred, CreatureSpawnEvent.SpawnReason.BREEDING));
        nerf.onHorseSpawn(new CreatureSpawnEvent(spawn(Cow.class), CreatureSpawnEvent.SpawnReason.NATURAL));

        assertStats(commanded, 20, 0.2, 0.7);
        assertTrue(nerf.isNerfed(commanded));
        assertStats(bred, 20, 0.2, 0.7);
        assertFalse(nerf.isNerfed(bred));
    }

    @Test
    void nothingIsNerfedWhileTheOptionIsOff() {
        Horse natural = horse(20, 0.2, 0.7);

        nerf.onHorseSpawn(new CreatureSpawnEvent(natural, CreatureSpawnEvent.SpawnReason.NATURAL));
        nerf.onChunkLoad(new ChunkLoadEvent(fakeChunk(0, 0, false, natural), true));

        assertStats(natural, 20, 0.2, 0.7);
        assertFalse(nerf.isNerfed(natural));
    }

    @Test
    void mountsInLoadedChunksAreNerfedUnlessAlreadyDone() {
        Numbers.nerfMountStats = true;
        Horse fresh = horse(20, 0.2, 0.7);
        Horse old = horse(20, 0.2, 0.7);
        Horse done = horse(20, 0.2, 0.7);
        nerf.setNerfedFlag(done, true);
        Cow cow = spawn(Cow.class);

        nerf.onChunkLoad(new ChunkLoadEvent(fakeChunk(0, 0, false, fresh, cow), true));
        nerf.onChunkLoad(new ChunkLoadEvent(fakeChunk(1, 0, false, old, done), false));

        assertStats(fresh, 10, 0.1, 0.35);
        assertStats(old, 10, 0.1, 0.35);
        assertStats(done, 20, 0.2, 0.7);
        assertEquals((byte) 1, done.getPersistentDataContainer().get(new NamespacedKey(plugin, "is_nerfed"), PersistentDataType.BYTE));
    }

    @Test
    void aBadDivisorIsLoggedInsteadOfBreakingTheSpawn() {
        Numbers.nerfMountStats = true;
        Numbers.mountNerfDivisor = 0;
        Horse still = horse(20, 0.2, 0);

        try (LogCapture log = new LogCapture(org.bukkit.Bukkit.getLogger())) {
            assertDoesNotThrow(() -> nerf.onHorseSpawn(new CreatureSpawnEvent(still, CreatureSpawnEvent.SpawnReason.NATURAL)));
            assertTrue(log.contains("[BreedinBuddies] Error while nerfing horse"));
        }
        assertTrue(nerf.isNerfed(still));
    }
}
