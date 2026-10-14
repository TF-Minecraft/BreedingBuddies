package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mockito.MockedStatic;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class HusbandryInspectionCoverageTest {
    @TempDir Path directory;
    HusbandryRuntimeCoverageTest.Farm f;
    private final HusbandryInspectListener listener = new HusbandryInspectListener();
    @BeforeEach void setUp() throws Exception { f = new HusbandryRuntimeCoverageTest.Farm(directory); }
    @AfterEach void tearDown() throws Exception { f.close(); }

    @Test void clickingABookInThePlayerInventoryDoesNotRemoveAnimalOwnership() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        HusbandryInspectGui.open(f.player, cow, animal); f.player.getInventory().setItem(9, new ItemStack(Material.WRITABLE_BOOK));
        InventoryClickEvent click = click(54); click.setCurrentItem(new ItemStack(Material.WRITABLE_BOOK));
        assertSame(f.player.getInventory(), click.getClickedInventory()); assertEquals(Material.WRITABLE_BOOK, click.getCurrentItem().getType()); listener.onClick(click);
        assertTrue(click.isCancelled()); assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid()), "Only the top inventory's remove button may revoke ownership");
        assertInstanceOf(HusbandryInspectHolder.class, f.player.getOpenInventory().getTopInventory().getHolder());
    }

    @Test void maximumLegalGeneticsProducesAFullBarWithoutIntegerOverflow() {
        assertEquals(5, HusbandryGuiBars.filledSegments(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test void unnamedAnimalsHaveAReadableInspectionTitleAndName() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); animal.setName(null);
        HusbandryInspectGui.open(f.player, cow, animal);
        assertEquals("§6Cow", f.player.getOpenInventory().getTitle()); assertEquals("§6Cow", name(f.player.getOpenInventory().getTopInventory(), 4));
    }

    @Test void inspectionShowsCareYieldOwnersProductsAndGrowth() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); animal.setCare(40); animal.setGenetics(600); f.own(animal, f.player);
        HusbandryInspectGui.open(f.player, cow, animal); Inventory inventory = f.player.getOpenInventory().getTopInventory();
        HusbandryInspectHolder holder = (HusbandryInspectHolder) inventory.getHolder(); assertEquals(animal.uuid(), holder.animalUuid()); assertSame(inventory, holder.getInventory());
        assertEquals(54, inventory.getSize()); assertEquals("§aHappy", name(inventory, 0)); assertEquals("§aNot neutered", name(inventory, 1));
        assertEquals(List.of("§7owner: Farmer"), lore(inventory, 2)); assertTrue(lore(inventory, 3).contains("§aYield 24%")); assertTrue(lore(inventory, 4).contains("§7Mature"));
        assertEquals(Material.WRITABLE_BOOK, inventory.getItem(8).getType());
        for (int slot = 20; slot < 25; slot++) assertEquals(slot < 22 ? Material.GREEN_CONCRETE : Material.GRAY_CONCRETE, inventory.getItem(slot).getType());
        assertEquals(List.of("§740/100"), lore(inventory, 20)); assertEquals(Material.GREEN_CONCRETE, inventory.getItem(40).getType()); assertEquals(Material.GRAY_CONCRETE, inventory.getItem(41).getType());
        animal.setNeutered(true); animal.setMatureAt(System.currentTimeMillis() + 3_600_000); animal.setHungrySince(System.currentTimeMillis());
        HusbandryInspectGui.open(f.neighbour, cow, animal); inventory = f.neighbour.getOpenInventory().getTopInventory(); assertNull(inventory.getItem(8)); assertEquals("§cNeutered", name(inventory, 1)); assertTrue(lore(inventory, 0).contains("§6Hungry")); assertTrue(lore(inventory, 0).stream().anyMatch(l -> l.startsWith("§eDecay in "))); assertTrue(lore(inventory, 4).get(2).startsWith("§7Grows up in "));
    }

    @Test void inspectionDisplaysMountStatsAndAllAfflictionStatuses() throws Exception {
        Horse horse = f.spawn(Horse.class); HusbandryAnimal animal = f.record(horse); HusbandryMounts.applyStats(horse, animal);
        HusbandryInspectGui.open(f.player, horse, animal); Inventory inventory = f.player.getOpenInventory().getTopInventory();
        assertEquals("§dHealth: 12.5 hearts", name(inventory, 46)); assertEquals("§bSpeed: 8.43 b/s", name(inventory, 49)); assertTrue(name(inventory, 52).endsWith(" blocks")); assertNull(inventory.getItem(3)); assertEquals(List.of("§7None (untamed)"), lore(inventory, 2));
        animal.setDirtySince(1L); HusbandryInspectGui.open(f.player, horse, animal); inventory = f.player.getOpenInventory().getTopInventory(); assertTrue(lore(inventory, 0).contains("§eDirty")); assertTrue(lore(inventory, 0).get(1).startsWith("§cDecaying (−1 / "));
        f.config.set("care-down-per-hour", 0.5); f.reload(); animal.setHungrySince(2L); HusbandryInspectGui.open(f.player, horse, animal); inventory = f.player.getOpenInventory().getTopInventory(); assertTrue(lore(inventory, 0).contains("§6Hungry")); assertTrue(lore(inventory, 0).get(2).startsWith("§cDecaying (−0.5 / "));
        animal.setDirtySince(null); HusbandryInspectGui.open(f.player, horse, animal); assertTrue(lore(f.player.getOpenInventory().getTopInventory(), 0).get(1).startsWith("§cDecaying"));
        Pig pig = f.spawn(Pig.class); HusbandryInspectGui.open(f.player, pig, f.record(pig)); assertNull(f.player.getOpenInventory().getTopInventory().getItem(3));
        HusbandryInspectGui.open(null, horse, animal); HusbandryInspectGui.open(f.player, null, animal); HusbandryInspectGui.open(f.player, horse, null);
    }

    @Test void ownerNamesFallBackToUuidWhenOfflineProfileHasNoName() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); UUID owner = UUID.randomUUID(); f.repository.upsertOwner(new HusbandryOwner(animal.uuid(), owner, owner, "coowner"));
        OfflinePlayer unnamed = mock(OfflinePlayer.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) { bukkit.when(() -> Bukkit.getOfflinePlayer(owner)).thenReturn(unnamed); HusbandryInspectGui.open(f.player, cow, animal); }
        assertEquals(List.of("§7coowner: " + owner), lore(f.player.getOpenInventory().getTopInventory(), 2));
        assertEquals(0, HusbandryGuiBars.yieldPercent(null)); assertEquals(0, HusbandryGuiBars.filledSegments(10, 0)); assertEquals(0, HusbandryGuiBars.filledSegments(0, 100)); assertEquals(5, HusbandryGuiBars.filledSegments(200, 100));
    }

    @Test void rightClickInspectHonoursHandRepositoryOwnershipStaffAndUnownedAccess() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player);
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.CLOCK));
        var offhand = new PlayerInteractEntityEvent(f.player, cow, EquipmentSlot.OFF_HAND); listener.onInteract(offhand); assertFalse(offhand.isCancelled());
        var display = interact(f.spawn(ItemDisplay.class)); listener.onInteract(display); assertFalse(display.isCancelled());
        when(f.plugin.getHusbandryRepository()).thenReturn(null); var unavailable = interact(cow); listener.onInteract(unavailable); assertFalse(unavailable.isCancelled()); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        var unknown = interact(f.spawn(Cow.class)); listener.onInteract(unknown); assertFalse(unknown.isCancelled());
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); var unrelated = interact(cow); listener.onInteract(unrelated); assertFalse(unrelated.isCancelled());
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.CLOCK)); var own = interact(cow); listener.onInteract(own); assertTrue(own.isCancelled()); assertInstanceOf(HusbandryInspectHolder.class, f.player.getOpenInventory().getTopInventory().getHolder());
        f.neighbour.getInventory().setItemInMainHand(new ItemStack(Material.CLOCK)); var denied = new PlayerInteractEntityEvent(f.neighbour, cow); listener.onInteract(denied); assertTrue(denied.isCancelled()); assertTrue(f.neighbour.nextMessage().contains("not your animal"));
        f.neighbour.setOp(true); listener.onInteract(new PlayerInteractEntityEvent(f.neighbour, cow)); assertInstanceOf(HusbandryInspectHolder.class, f.neighbour.getOpenInventory().getTopInventory().getHolder());
        f.neighbour.setOp(false); HusbandryOwnershipService.removeSelf(f.player, animal); f.player.getInventory().setItemInMainHand(null); f.player.setSneaking(true); listener.onInteract(interact(cow)); assertInstanceOf(HusbandryInspectHolder.class, f.player.getOpenInventory().getTopInventory().getHolder());
        f.player.setSneaking(false); var empty = interact(cow); listener.onInteract(empty); assertFalse(empty.isCancelled());
    }

    @Test void inspectionItemsNeverOverrideFeedingTamingSharingMilkingOrNeutering() throws Exception {
        Cow cow = f.spawn(Cow.class); f.record(cow);
        Map<String, Material> paths = new LinkedHashMap<>(); paths.put("feed", Material.WHEAT); paths.put("glove", Material.LEATHER); paths.put("tame", Material.NAME_TAG); paths.put("share", Material.PAPER); paths.put("neuter", Material.SHEARS); paths.put("bucket", Material.BUCKET);
        for (var path : paths.entrySet()) { f.config.set("items.inspect", path.getKey()); f.reload(); f.player.getInventory().setItemInMainHand(new ItemStack(path.getValue())); var event = interact(cow); listener.onInteract(event); assertFalse(event.isCancelled(), path.getKey()); }
    }

    @Test void removeButtonReleasesOnlyThisOwnerAndHandlesConcurrentDeletionOrLostOwnership() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player); HusbandryInspectGui.open(f.player, cow, animal);
        InventoryClickEvent unrelated = click(4); listener.onClick(unrelated); assertTrue(unrelated.isCancelled()); assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid())); listener.onClick(click(5));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); listener.onClick(click(8)); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository); assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid()));
        listener.onClick(click(8)); assertFalse(HusbandryOwnershipService.isOwner(f.player, animal.uuid())); assertEquals(HusbandryAnimalState.UNTAMED, f.repository.getAnimal(animal.uuid()).orElseThrow().state()); assertTrue(f.player.nextMessage().contains("no longer own"));
        f.own(animal, f.player); HusbandryInspectGui.open(f.player, cow, animal); f.repository.deleteCharacterOwner(animal.uuid(), f.player.getUniqueId()); listener.onClick(click(8)); assertTrue(f.player.nextMessage().contains("Could not remove"));
        f.own(animal, f.player); HusbandryInspectGui.open(f.player, cow, animal); HusbandryEntities.evict(animal.uuid()); f.repository.deleteAnimal(animal.uuid()); listener.onClick(click(8)); assertNull(f.player.getOpenInventory().getTopInventory());
        f.player.openInventory(Bukkit.createInventory(null, 9));
        var ordinary = click(0); listener.onClick(ordinary); assertFalse(ordinary.isCancelled());
    }

    @Test void ownershipClaimsRenameEnforceCapsAndPermitStaff() {
        Cow first = f.spawn(Cow.class), second = f.spawn(Cow.class), third = f.spawn(Cow.class); HusbandryAnimal a = f.record(first), b = f.record(second), c = f.record(third);
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.claimOwner(f.player, a, first, "  Bessie  ")); assertEquals("Bessie", a.name()); assertEquals("Bessie", first.getCustomName());
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.claimOwner(f.player, a, first, "  Renamed  ")); assertEquals("Renamed", f.repository.getAnimal(a.uuid()).orElseThrow().name());
        assertEquals(HusbandryOwnershipService.Result.NOT_OWNER, HusbandryOwnershipService.claimOwner(f.neighbour, a, first, "Steal"));
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.claimOwner(f.player, b, second, "???")); assertEquals("Cow", b.name());
        assertFalse(HusbandryOwnershipService.canAccept(f.player)); assertEquals(HusbandryOwnershipService.Result.AT_CAP, HusbandryOwnershipService.claimOwner(f.player, c, third, "Third")); assertFalse(HusbandryOwnershipService.hasAnyOwner(c.uuid()));
        f.player.setOp(true); assertTrue(HusbandryOwnershipService.canAccept(f.player)); assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.claimOwner(f.player, c, third, "Third")); assertEquals(3, f.repository.countForPlayer(f.player.getUniqueId()));
        assertFalse(HusbandryOwnershipService.isOwner((Player) null, a.uuid())); assertFalse(HusbandryOwnershipService.isOwner((net.tfminecraft.tlibs.character.CharacterRef) null, a.uuid())); assertFalse(HusbandryOwnershipService.isOwner(new net.tfminecraft.tlibs.character.CharacterRef(f.player.getUniqueId(), f.player.getUniqueId()), null)); assertFalse(HusbandryOwnershipService.hasAnyOwner(null)); assertFalse(HusbandryOwnershipService.isStaff(null));
    }

    @Test void coOwnershipKeepsStateUntilLastOwnerLeavesAndChecksActorAndTargetCaps() {
        HusbandryAnimal animal = f.record(f.spawn(Cow.class));
        assertEquals(HusbandryOwnershipService.Result.NOT_OWNER, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal)); f.own(animal, f.player);
        var stranger = f.server.addPlayer("Stranger"); f.characters.activate(stranger, stranger.getUniqueId()); assertEquals(HusbandryOwnershipService.Result.NOT_OWNER, HusbandryOwnershipService.addCoOwner(stranger, f.neighbour, animal));
        f.own(f.offline("COW", "One"), f.neighbour); f.own(f.offline("COW", "Two"), f.neighbour);
        assertEquals(HusbandryOwnershipService.Result.TARGET_AT_CAP, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal));
        f.neighbour.setOp(true); assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal)); assertEquals(HusbandryOwnershipService.Result.ALREADY_TARGET_OWNER, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal));
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.removeSelf(f.player, animal)); assertEquals(HusbandryAnimalState.OWNED, f.repository.getAnimal(animal.uuid()).orElseThrow().state());
        assertEquals(HusbandryOwnershipService.Result.NOT_OWNER, HusbandryOwnershipService.removeSelf(f.player, animal));
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.removeSelf(f.neighbour, animal)); assertEquals(HusbandryAnimalState.UNTAMED, f.repository.getAnimal(animal.uuid()).orElseThrow().state()); assertTrue(HusbandryOwnershipService.listOwners(animal.uuid()).isEmpty());
        f.own(animal, f.player); assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.addCoOwner(stranger, stranger, animal)); assertEquals("coowner", f.repository.listOwners(animal.uuid()).stream().filter(o -> o.playerUuid().equals(stranger.getUniqueId())).findFirst().orElseThrow().role());
    }

    @Test void ownershipUnavailableArgumentsAndNamingFallbacksAreSafe() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow);
        assertFalse(HusbandryOwnershipService.canAccept(null));
        assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.claimOwner(null, animal, cow, "Name")); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.claimOwner(f.player, null, cow, "Name"));
        assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.addCoOwner(null, f.neighbour, animal)); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.addCoOwner(f.player, null, animal)); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, null));
        assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.removeSelf(null, animal)); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.removeSelf(f.player, null));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); assertFalse(HusbandryOwnershipService.canAccept(f.player)); assertFalse(HusbandryOwnershipService.isOwner(f.player, animal.uuid())); assertFalse(HusbandryOwnershipService.hasAnyOwner(animal.uuid())); assertTrue(HusbandryOwnershipService.listOwners(animal.uuid()).isEmpty());
        assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.claimOwner(f.player, animal, cow, "Name")); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal)); assertEquals(HusbandryOwnershipService.Result.NO_REPOSITORY, HusbandryOwnershipService.removeSelf(f.player, animal)); when(f.plugin.getHusbandryRepository()).thenReturn(f.repository);
        animal.setName(""); HusbandryOwnershipService.applyName(animal, cow, "???"); assertEquals("Cow", animal.name()); animal.setName(""); HusbandryOwnershipService.applyName(animal, null, null); assertEquals("Animal", animal.name());
        HusbandryOwnershipService.applyName(animal, null, "  "); assertEquals("Animal", animal.name()); cow.remove(); HusbandryOwnershipService.applyName(animal, cow, "Removed"); assertEquals("Removed", animal.name());
    }

    @Test void stateDisplayMigratesOnlyLegacyLabelsAndShowsEveryAffliction() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); animal.setName(null);
        TextDisplay legacy = f.spawn(TextDisplay.class), unrelated = f.spawn(TextDisplay.class); Pig passenger = f.spawn(Pig.class);
        legacy.getPersistentDataContainer().set(new NamespacedKey("cooking", "state_label"), PersistentDataType.STRING, "old"); cow.addPassenger(legacy); cow.addPassenger(unrelated); cow.addPassenger(passenger);
        HusbandryStateDisplay.sync(cow, animal); assertFalse(legacy.isValid()); assertTrue(unrelated.isValid()); assertTrue(passenger.isValid()); assertEquals("Cow", cow.getCustomName()); assertFalse(cow.isCustomNameVisible());
        animal.setName("Bess"); animal.setHungrySince(1L); HusbandryStateDisplay.sync(cow, animal); assertEquals("Bess (Hungry)", cow.getCustomName()); assertTrue(cow.isCustomNameVisible());
        animal.setDirtySince(1L); HusbandryStateDisplay.sync(cow, animal); assertEquals("Bess (Hungry, Dirty)", cow.getCustomName()); animal.setHungrySince(null); HusbandryStateDisplay.sync(cow, animal); assertEquals("Bess (Dirty)", cow.getCustomName());
        HusbandryStateDisplay.removeAll(cow); assertTrue(unrelated.isValid()); HusbandryStateDisplay.sync(null, animal); HusbandryStateDisplay.sync(cow, null); HusbandryStateDisplay.removeAll(null); cow.remove(); HusbandryStateDisplay.sync(cow, animal); HusbandryStateDisplay.removeAll(cow);
    }

    @Test void mountAccessAllowsOwnersAndStaffAndRemindsUnclaimedRiders() throws Exception {
        HusbandryMountListener mounts = new HusbandryMountListener(); Horse horse = f.spawn(Horse.class); Cow cow = f.spawn(Cow.class);
        var notMount = new EntityMountEvent(f.player, cow); mounts.onMount(notMount); assertFalse(notMount.isCancelled()); var notPlayer = new EntityMountEvent(cow, horse); mounts.onMount(notPlayer); assertFalse(notPlayer.isCancelled());
        f.config.set("remove-unowned", List.of("HORSE")); f.reload(); var unclaimed = new EntityMountEvent(f.player, horse); mounts.onMount(unclaimed); assertFalse(unclaimed.isCancelled()); assertTrue(f.player.nextMessage().contains("not claimed"));
        HusbandryAnimal animal = HusbandryEntities.lookup(horse.getUniqueId()).orElseThrow(); f.own(animal, f.player);
        var own = new EntityMountEvent(f.player, horse); mounts.onMount(own); assertFalse(own.isCancelled()); var other = new EntityMountEvent(f.neighbour, horse); mounts.onMount(other); assertTrue(other.isCancelled()); assertTrue(f.neighbour.nextMessage().contains("not your animal"));
        f.neighbour.setOp(true); var staff = new EntityMountEvent(f.neighbour, horse); mounts.onMount(staff); assertFalse(staff.isCancelled());
        mounts.onTame(new EntityTameEvent(horse, f.player)); mounts.onTame(new EntityTameEvent(horse, Bukkit.getOfflinePlayer(UUID.randomUUID()))); mounts.onQuit(new PlayerQuitEvent(f.player, "bye"));
        when(f.plugin.getHusbandryRepository()).thenReturn(null); var unavailable = new EntityMountEvent(f.player, f.spawn(Horse.class)); mounts.onMount(unavailable); assertTrue(unavailable.isCancelled());
    }

    @Test void removalIgnoresOtherHumanViewersAndAReplacedButton() {
        Cow cow = f.spawn(Cow.class); HusbandryAnimal animal = f.record(cow); f.own(animal, f.player); HusbandryInspectGui.open(f.player, cow, animal);
        Inventory top = f.player.getOpenInventory().getTopInventory(); InventoryView view = mock(InventoryView.class); HumanEntity viewer = mock(HumanEntity.class);
        when(view.getTopInventory()).thenReturn(top); when(view.getInventory(8)).thenReturn(top); when(view.getPlayer()).thenReturn(viewer);
        InventoryClickEvent nonPlayer = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 8, ClickType.LEFT, InventoryAction.PICKUP_ALL); listener.onClick(nonPlayer); assertTrue(nonPlayer.isCancelled()); assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid()));
        top.setItem(8, new ItemStack(Material.STONE)); listener.onClick(click(8)); top.setItem(8, null); listener.onClick(click(8)); assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid()));
        InventoryClickEvent outside = new InventoryClickEvent(f.player.getOpenInventory(), InventoryType.SlotType.OUTSIDE, -999, ClickType.LEFT, InventoryAction.NOTHING); listener.onClick(outside); assertTrue(outside.isCancelled());
    }

    private PlayerInteractEntityEvent interact(Entity entity) { return new PlayerInteractEntityEvent(f.player, entity, EquipmentSlot.HAND); }
    private static List<String> lore(Inventory inventory, int slot) { return inventory.getItem(slot).getItemMeta().getLore(); }

    private InventoryClickEvent click(int rawSlot) { return new InventoryClickEvent(f.player.getOpenInventory(), InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL); }
    private static String name(Inventory inventory, int slot) { return inventory.getItem(slot).getItemMeta().getDisplayName(); }
}
