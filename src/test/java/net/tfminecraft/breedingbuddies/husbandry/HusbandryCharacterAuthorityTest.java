package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.estate.*;
import static net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipStore.*;

class HusbandryCharacterAuthorityTest {
    @TempDir Path directory;
    HusbandryRuntimeCoverageTest.Farm farm;
    @BeforeEach void open() throws Exception { farm = new HusbandryRuntimeCoverageTest.Farm(directory); }
    @AfterEach void close() throws Exception { farm.close(); }

    @Test void switchingDoesNotGrantOwnersMenusTameOrMountRightsAndOutgoingSessionDismounts() throws Exception {
        var f = farm; var horse = f.spawn(Horse.class); var animal = f.record(horse); f.own(animal, f.player);
        CharacterSession outgoing = HusbandryIdentity.session(f.player).orElseThrow();
        HusbandryInspectGui.open(f.player, horse, animal);
        var holder = (HusbandryInspectHolder)f.player.getOpenInventory().getTopInventory().getHolder();
        assertTrue(holder.isCurrent(f.player)); assertTrue(holder.belongsTo(outgoing));
        assertFalse(HusbandryIdentity.current(null, f.player)); assertFalse(HusbandryIdentity.current(outgoing, null));
        assertFalse(HusbandryIdentity.current(outgoing, f.neighbour));
        horse.addPassenger(f.player); assertEquals(horse, f.player.getVehicle());
        f.characters.set(f.player, null);
        new HusbandryMountListener().onCharacterEnd(new CharacterSessionEndEvent(f.player, outgoing, CharacterSessionEndEvent.Reason.SWITCH));
        assertNull(f.player.getVehicle()); assertNull(f.player.getOpenInventory().getTopInventory());
        f.characters.activate(f.player, UUID.randomUUID());
        assertFalse(HusbandryOwnershipService.isOwner(f.player, animal.uuid())); assertFalse(holder.isCurrent(f.player));
        var mount = new EntityMountEvent(f.player, horse); new HusbandryMountListener().onMount(mount); assertTrue(mount.isCancelled());
        var tame = new EntityTameEvent(horse, f.player); new HusbandryMountListener().protectTame(tame); assertTrue(tame.isCancelled());
        f.player.openInventory(holder.getInventory());
        var click = new InventoryClickEvent(f.player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 8, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        new HusbandryInspectListener().onClick(click); assertTrue(click.isCancelled());
        assertEquals(1, f.repository.listOwners(animal.uuid()).size());
        var other = HusbandryIdentity.session(f.player).orElseThrow();
        new HusbandryMountListener().onCharacterEnd(new CharacterSessionEndEvent(f.neighbour, other, CharacterSessionEndEvent.Reason.QUIT));
        new HusbandryMountListener().onCharacterEnd(new CharacterSessionEndEvent(f.player, other, CharacterSessionEndEvent.Reason.QUIT));
        new HusbandryMountListener().protectTame(new EntityTameEvent(horse, f.server.getOfflinePlayer(UUID.randomUUID())));
        f.player.setOp(true); var staffTame = new EntityTameEvent(horse, f.player); new HusbandryMountListener().protectTame(staffTame); assertFalse(staffTame.isCancelled());
    }

    @Test void resolvedCoownerRetainsRightsHeldSlotBlocksTokensAndDistinctAccountCapCannotBeMultiplied() throws Exception {
        var f = farm; var animal = f.record(f.spawn(Cow.class));
        update(f.repository.ownershipDatabase().getConnection(), "INSERT INTO owners VALUES (?, ?, 'owner')", animal.uuid().toString(), f.player.getUniqueId().toString());
        animal.setState(HusbandryAnimalState.OWNED); f.repository.upsertAnimal(animal);
        f.repository.upsertOwner(new HusbandryOwner(animal.uuid(), f.neighbour.getUniqueId(), f.neighbour.getUniqueId(), "coowner"));
        assertTrue(HusbandryOwnershipService.isOwner(f.neighbour, animal.uuid()));
        assertEquals(HusbandryOwnershipService.Result.HELD_OWNER, HusbandryOwnershipService.addCoOwner(f.player, f.player, animal));
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.removeSelf(f.neighbour, animal));
        assertTrue(HusbandryOwnershipService.hasAnyOwner(animal.uuid()));
        assertEquals(HusbandryAnimalState.OWNED, animal.state()); // the held slot still owns the animal
        f.own(animal, f.player); var a = HusbandryIdentity.session(f.player).orElseThrow();
        var extra = f.ownedOffline("Second"); assertFalse(HusbandryOwnershipService.canAccept(f.player));
        UUID characterB = UUID.randomUUID(); f.characters.activate(f.player, characterB);
        assertFalse(HusbandryOwnershipService.canAccept(f.player));
        f.repository.upsertOwner(new HusbandryOwner(animal.uuid(), f.neighbour.getUniqueId(), f.neighbour.getUniqueId(), "coowner"));
        // Explicit resolved grant of another character's slot on the SAME animal does not consume capacity twice.
        assertEquals(HusbandryOwnershipService.Result.OK, HusbandryOwnershipService.addCoOwner(f.neighbour, f.player, animal));
        assertTrue(HusbandryOwnershipService.isOwner(f.player, animal.uuid())); assertEquals(2, f.repository.countForPlayer(f.player.getUniqueId()));
        assertEquals(1, f.repository.listForCharacter(new CharacterRef(f.player.getUniqueId(), characterB)).size());
        f.characters.set(f.player, null);
        assertFalse(HusbandryOwnershipService.canAccept(f.player));
        assertEquals(HusbandryOwnershipService.Result.CHARACTER_REQUIRED, HusbandryOwnershipService.claimOwner(f.player, animal, null, "Late"));
        assertEquals(HusbandryOwnershipService.Result.CHARACTER_REQUIRED, HusbandryOwnershipService.addCoOwner(f.neighbour, f.player, animal));
        assertEquals(HusbandryOwnershipService.Result.CHARACTER_REQUIRED, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal));
        var noSessionMount = new EntityMountEvent(f.player, f.spawn(Horse.class)); new HusbandryMountListener().onMount(noSessionMount); assertTrue(noSessionMount.isCancelled());
    }

    @Test void strictEstateFacadeRejectsRevivalRedeathOwnerMismatchAndNewRevision() throws Exception {
        var f = farm; var source = HusbandryIdentity.session(f.player).orElseThrow().ref(); var recipient = HusbandryIdentity.session(f.neighbour).orElseThrow().ref();
        var animal = f.record(f.spawn(Cow.class)); f.own(animal, f.player);
        f.own(f.record(f.spawn(Cow.class)), f.neighbour);
        f.config.set("max-animals", 1); f.reload();
        assertFalse(HusbandryOwnershipService.canAccept(f.neighbour));
        UUID death = UUID.randomUUID(); var decision = new EstateDecision(death, source, Optional.of(Instant.EPOCH), Instant.EPOCH,
                Optional.of(recipient), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.character(recipient), Recipient.FROZEN, Recipient.SENDER, 1);
        var service = mock(EstateService.class); f.server.getServicesManager().register(EstateService.class, service, f.plugin, ServicePriority.Normal);
        when(service.decision(death)).thenReturn(Optional.of(decision)); when(service.stateOf(source.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(f.characters.provider.deathId(source.characterId())).thenReturn(Optional.of(death));
        when(f.characters.provider.info(source.characterId())).thenReturn(Optional.of(new CharacterInfo(source, "A", CharacterStatus.ALIVE, null, Set.of(), 0, null)));
        var estate = new HusbandryEstate(f.repository, Logger.getAnonymousLogger()); assertEquals(Outcome.Kind.RETRY, estate.settle(decision).kind());
        when(f.characters.provider.info(source.characterId())).thenReturn(Optional.of(new CharacterInfo(source, "A", CharacterStatus.DEAD, null, Set.of(), 0, null)));
        when(f.characters.provider.deathId(source.characterId())).thenReturn(Optional.of(UUID.randomUUID())); assertEquals(Outcome.Kind.RETRY, estate.settle(decision).kind());
        when(f.characters.provider.deathId(source.characterId())).thenReturn(Optional.of(death)); when(service.decision(death)).thenReturn(Optional.empty()); assertEquals(Outcome.Kind.RETRY, estate.settle(decision).kind());
        when(service.decision(death)).thenReturn(Optional.of(decision));
        when(f.characters.provider.info(source.characterId())).thenReturn(Optional.of(new CharacterInfo(new CharacterRef(recipient.ownerId(), source.characterId()), "Conflict", CharacterStatus.DEAD, null, Set.of(), 0, null)));
        assertEquals(Outcome.Kind.RETRY, estate.settle(decision).kind());
        when(f.characters.provider.info(source.characterId())).thenReturn(Optional.of(new CharacterInfo(source, "A", CharacterStatus.DEAD, null, Set.of(), 0, null)));
        assertEquals(Outcome.Kind.DONE, estate.settle(decision).kind()); assertEquals(recipient, f.repository.listOwners(animal.uuid()).getFirst().ref());
        assertEquals(2, f.repository.countForPlayer(recipient.ownerId()));
        assertTrue(f.repository.exists(animal.uuid())); assertFalse(HusbandryOwnershipService.canAccept(f.neighbour));
        when(service.decided()).thenReturn(List.of(decision)); estate.reconcile(); verify(service).report(eq(death), eq("cooking-animals"), eq(1L), any());
    }

    @Test void migrationReconcileAndCapabilityProbeHandleAbsentOldAndFailingProviders() throws Exception {
        var f = farm; assertTrue(HusbandryIdentity.supports(HusbandryIdentity.class.getClassLoader(), true));
        ClassLoader absent = new ClassLoader(HusbandryIdentity.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("net.tfminecraft.tlibs.character.Characters")) throw new ClassNotFoundException("Missing old API");
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(HusbandryIdentity.supports(absent, false));
        ClassLoader old = new ClassLoader(HusbandryIdentity.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.tfminecraft.tlibs.character.estate.")) throw new ClassNotFoundException("Old TLibs");
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(HusbandryIdentity.supports(old, true)); assertTrue(HusbandryIdentity.supports(old, false));
        assertEquals(0, HusbandryIdentity.session(null).stream().count());
        HusbandryIdentity.start(f.plugin, null);
        var plugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("AnimalEstateFixture");
        HusbandryIdentity.start(plugin, f.repository);
        f.server.getScheduler().performTicks(2);
        assertEquals("cooking-animals", f.server.getServicesManager().load(EstateParticipant.class).id());
        when(f.characters.provider.legacyHeirsReady()).thenReturn(true);
        when(f.characters.provider.legacyHeir(any())).thenReturn(Optional.empty());
        var held = f.ownedOffline("Unknown"); update(f.repository.ownershipDatabase().getConnection(), "INSERT INTO owners VALUES (?, ?, 'owner')", held.uuid().toString(), UUID.randomUUID().toString());
        HusbandryMigration.reconcile(plugin, f.repository); assertEquals(1, f.repository.heldOwners());
        doThrow(new IllegalStateException("Filesystem unavailable")).when(f.repository).migrateOwners(any(), any());
        assertDoesNotThrow(() -> HusbandryMigration.reconcile(plugin, f.repository));
    }

    @Test void noSessionTamingTokensAndRosterFailClosedWithoutConsumingItems() throws Exception {
        var f = farm; var cow = f.spawn(Cow.class); var animal = f.record(cow);
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.NAME_TAG, 2));
        HusbandryItems.setTameName(f.player.getInventory().getItemInMainHand(), "A's animal");
        f.characters.set(f.player, null);
        var listener = new HusbandryTamingListener();
        listener.onInteractEntity(new PlayerInteractEntityEvent(f.player, cow, EquipmentSlot.HAND));
        assertEquals(2, f.player.getInventory().getItemInMainHand().getAmount());
        assertFalse(f.repository.hasAnyOwner(animal.uuid()));
        new HusbandryAnimalsCommand().onCommand(f.player, mock(org.bukkit.command.Command.class), "animals", new String[0]);
        update(f.repository.ownershipDatabase().getConnection(), "INSERT INTO owners VALUES (?, ?, 'owner')", animal.uuid().toString(), f.player.getUniqueId().toString());
        var token = new ItemStack(Material.PAPER, 2); HusbandryItems.setLinkedAnimal(token, animal.uuid().toString(), "Animal");
        f.player.getInventory().setItemInMainHand(token);
        listener.onInteractEntity(new PlayerInteractEntityEvent(f.player, f.neighbour, EquipmentSlot.HAND));
        assertEquals(2, token.getAmount());
        f.characters.activate(f.player, f.player.getUniqueId());
        listener.onInteractEntity(new PlayerInteractEntityEvent(f.player, f.neighbour, EquipmentSlot.HAND));
        assertEquals(2, token.getAmount()); assertTrue(f.repository.hasHeldOwner(animal.uuid()));
    }

    @Test void oldApiStartDefersMigrationAndEstateRegistration() {
        var f = farm; var plugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("OldAnimalApi");
        try (var bridge = mockStatic(HusbandryIdentity.class, CALLS_REAL_METHODS)) {
            bridge.when(() -> HusbandryIdentity.supports(any(), eq(false))).thenReturn(false);
            HusbandryIdentity.start(plugin, f.repository);
            assertNull(f.server.getServicesManager().load(EstateParticipant.class));
            bridge.when(() -> HusbandryIdentity.supports(any(), eq(false))).thenReturn(true);
            bridge.when(() -> HusbandryIdentity.supports(any(), eq(true))).thenReturn(false);
            HusbandryIdentity.start(plugin, f.repository);
            assertNull(f.server.getServicesManager().load(EstateParticipant.class));
        }
    }

    @Test void stalePreparedEstateHoldsOwnershipWritersWithoutRevokingResolvedCoownerRights() throws Exception {
        var f = farm; var cow = f.spawn(Cow.class); var animal = f.record(cow); f.own(animal, f.player);
        f.repository.upsertOwner(new HusbandryOwner(animal.uuid(), f.neighbour.getUniqueId(), f.neighbour.getUniqueId(), "coowner"));
        update(f.repository.ownershipDatabase().getConnection(), "INSERT INTO husbandry_estate_intents VALUES (?,1,'old decision',?,?,?,?,'prepared')",
                UUID.randomUUID().toString(), f.player.getUniqueId().toString(), f.player.getUniqueId().toString(),
                f.neighbour.getUniqueId().toString(), f.neighbour.getUniqueId().toString());
        assertTrue(f.repository.isEstateHeld(animal.uuid())); assertFalse(f.repository.isEstateHeld(null));
        assertTrue(HusbandryOwnershipService.isOwner(f.neighbour, animal.uuid()));
        assertEquals(HusbandryOwnershipService.Result.HELD_OWNER, HusbandryOwnershipService.claimOwner(f.player, animal, cow, "Not rewritten"));
        assertEquals(HusbandryOwnershipService.Result.HELD_OWNER, HusbandryOwnershipService.addCoOwner(f.player, f.neighbour, animal));
        assertEquals(HusbandryOwnershipService.Result.HELD_OWNER, HusbandryOwnershipService.removeSelf(f.neighbour, animal));
        var owner = new HusbandryOwner(animal.uuid(), f.player.getUniqueId(), f.player.getUniqueId(), "owner");
        assertThrows(IllegalStateException.class, () -> f.repository.upsertOwner(owner));
        assertThrows(IllegalStateException.class, () -> f.repository.saveAnimalAndOwner(animal, owner));
        assertThrows(IllegalStateException.class, () -> f.repository.deleteCharacterOwner(animal.uuid(), owner.characterUuid()));
        f.player.getInventory().setItemInMainHand(new ItemStack(Material.NAME_TAG,2)); HusbandryItems.setTameName(f.player.getInventory().getItemInMainHand(), "Held");
        new HusbandryTamingListener().onInteractEntity(new PlayerInteractEntityEvent(f.player,cow,EquipmentSlot.HAND));
        assertEquals(2,f.player.getInventory().getItemInMainHand().getAmount());
        assertEquals(2,f.repository.listOwners(animal.uuid()).size());
        update(f.repository.ownershipDatabase().getConnection(), "INSERT INTO owners VALUES (?,?,'owner')", animal.uuid().toString(), UUID.randomUUID().toString());
        HusbandryInspectGui.open(f.neighbour,cow,animal);
        assertTrue(f.neighbour.getOpenInventory().getTopInventory().getItem(2).getItemMeta().getLore().stream().anyMatch(line->line.contains("Unresolved")));
    }

    @Test void vanillaTameableInteractionsCannotUseAccountOwnerWhilePublicLivestockInteractionStays() {
        var f = farm; var horse = f.spawn(Horse.class); var animal = f.record(horse); f.own(animal, f.player);
        var listener = new HusbandryMountListener();
        var owner = new PlayerInteractEntityEvent(f.player,horse,EquipmentSlot.HAND); listener.protectInteract(owner); assertFalse(owner.isCancelled());
        f.characters.activate(f.player,UUID.randomUUID());
        var alternate = new PlayerInteractEntityEvent(f.player,horse,EquipmentSlot.HAND); listener.protectInteract(alternate); assertTrue(alternate.isCancelled());
        f.player.setOp(true); var staff = new PlayerInteractEntityEvent(f.player,horse,EquipmentSlot.HAND); listener.protectInteract(staff); assertFalse(staff.isCancelled());
        f.player.setOp(false); var wild = new PlayerInteractEntityEvent(f.player,f.spawn(Horse.class),EquipmentSlot.HAND); listener.protectInteract(wild); assertFalse(wild.isCancelled());
        var cow=f.spawn(Cow.class); var livestock=f.record(cow); f.own(livestock,f.neighbour);
        var publicHarvest=new PlayerInteractEntityEvent(f.player,cow,EquipmentSlot.HAND);listener.protectInteract(publicHarvest);assertFalse(publicHarvest.isCancelled());
    }

    @Test void providerReturnedStaleAndWrongAccountSessionsCannotGrantActionOrMenuAuthority() throws Exception {
        var f=farm;var horse=f.spawn(Horse.class);var animal=f.record(horse);f.own(animal,f.player);
        var before=HusbandryIdentity.session(f.player).orElseThrow();
        when(f.characters.provider.currentSession(f.player)).thenReturn(Optional.of(before));
        when(f.characters.provider.isCurrent(before)).thenReturn(false);
        assertTrue(HusbandryIdentity.session(f.player).isEmpty());assertFalse(HusbandryOwnershipService.isOwner(f.player,animal.uuid()));
        assertEquals(HusbandryOwnershipService.Result.CHARACTER_REQUIRED,HusbandryOwnershipService.claimOwner(f.player,animal,horse,"Stale"));
        HusbandryInspectGui.open(f.player,horse,animal);
        var holder=(HusbandryInspectHolder)f.player.getOpenInventory().getTopInventory().getHolder();assertFalse(holder.isCurrent(f.player));
        var click=new InventoryClickEvent(f.player.getOpenInventory(),InventoryType.SlotType.CONTAINER,8,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        new HusbandryInspectListener().onClick(click);assertEquals(1,f.repository.listOwners(animal.uuid()).size());
        var staleMount=new EntityMountEvent(f.player,horse);new HusbandryMountListener().onMount(staleMount);assertTrue(staleMount.isCancelled());
        var wrong=HusbandryIdentity.session(f.neighbour).orElseThrow();
        when(f.characters.provider.currentSession(f.player)).thenReturn(Optional.of(wrong));
        assertTrue(f.characters.provider.isCurrent(wrong));assertTrue(HusbandryIdentity.session(f.player).isEmpty());
        assertFalse(HusbandryOwnershipService.canAccept(f.player));assertFalse(HusbandryOwnershipService.isOwner(f.player,animal.uuid()));
        assertEquals(HusbandryOwnershipService.Result.CHARACTER_REQUIRED,HusbandryOwnershipService.claimOwner(f.player,animal,horse,"Wrong owner"));
        var wrongMount=new EntityMountEvent(f.player,horse);new HusbandryMountListener().onMount(wrongMount);assertTrue(wrongMount.isCancelled());
        new HusbandryAnimalsCommand().onCommand(f.player,null,"animals",new String[0]);
        assertEquals(before.ref(),f.repository.listOwners(animal.uuid()).getFirst().ref());
    }

    @Test void sessionStartEjectsOnlyUnauthorizedCurrentRiderAndOwnerTamingStaysAllowed() throws Exception {
        var f = farm; var horse = f.spawn(Horse.class); var animal = f.record(horse); f.own(animal, f.player);
        horse.addPassenger(f.player); var owner = HusbandryIdentity.session(f.player).orElseThrow();
        var listener = new HusbandryMountListener(); listener.onCharacterStart(new CharacterSessionStartEvent(f.player, owner)); assertEquals(horse, f.player.getVehicle());
        var tame = new EntityTameEvent(horse, f.player); listener.protectTame(tame); assertFalse(tame.isCancelled());
        f.characters.activate(f.player, UUID.randomUUID()); listener.onCharacterStart(new CharacterSessionStartEvent(f.player, owner)); assertEquals(horse, f.player.getVehicle());
        listener.onCharacterStart(new CharacterSessionStartEvent(f.player, HusbandryIdentity.session(f.player).orElseThrow())); assertNull(f.player.getVehicle());
        listener.onCharacterStart(new CharacterSessionStartEvent(f.player, HusbandryIdentity.session(f.player).orElseThrow()));
        horse.addPassenger(f.player); f.player.setOp(true); listener.onCharacterStart(new CharacterSessionStartEvent(f.player, HusbandryIdentity.session(f.player).orElseThrow())); assertEquals(horse, f.player.getVehicle());
        var unowned = f.spawn(Horse.class); listener.protectTame(new EntityTameEvent(unowned, f.neighbour));
        f.player.leaveVehicle(); unowned.addPassenger(f.player); listener.onCharacterStart(new CharacterSessionStartEvent(f.player, HusbandryIdentity.session(f.player).orElseThrow())); assertEquals(unowned, f.player.getVehicle());
    }
}
