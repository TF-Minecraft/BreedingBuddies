package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.listeners.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRegressionTest extends PluginTestBase {
    private CharacterSession select(Player player) {
        var session = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 7L);
        assertNotEquals(session.ref().ownerId(), session.ref().characterId());
        sessions.put(player.getUniqueId(), session);
        return session;
    }
    @Test void unavailableOwnershipBlocksConfiguredAnimalsButAllowsOtherEntitiesAndSpecies() {
        var player = player(); var session = select(player);
        Horse horse = spawn(Horse.class); own(player, horse);
        assertEquals(Set.of(session.ref().characterId()), OwnershipManager.getAnimal(horse.getUniqueId()).getOwnerRefs().keySet());
        var config = new YamlConfiguration(); config.set("HORSE", Map.of()); new AnimalUtils(config);
        PluginData.ownership().ready = false;
        var listener = new MountListener();
        for (EntityType type : List.of(EntityType.VILLAGER, EntityType.ITEM_FRAME, EntityType.PLAYER, EntityType.ZOMBIE, EntityType.PIG)) {
            Entity entity = mock(Entity.class); when(entity.getType()).thenReturn(type); when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            var event = new PlayerInteractEntityEvent(player, entity, EquipmentSlot.HAND);
            listener.onInteract(event); assertFalse(event.isCancelled(), type.toString());
        }
        Animals pig = mock(Animals.class); when(pig.getType()).thenReturn(EntityType.PIG); when(pig.getUniqueId()).thenReturn(UUID.randomUUID());
        var unrelatedMount = new EntityMountEvent(player, pig); listener.onMount(unrelatedMount); assertFalse(unrelatedMount.isCancelled());
        var farmClick = new PlayerInteractEntityEvent(player, horse, EquipmentSlot.HAND);
        listener.onInteract(farmClick); assertTrue(farmClick.isCancelled());
        var farmMount = new EntityMountEvent(player, horse); listener.onMount(farmMount); assertTrue(farmMount.isCancelled());
    }
    @Test void ordinaryFeedingDoesNotEmitTokenReadinessMessageButOwnershipTokensStillRefuse() {
        var player = player(); select(player); Cow cow = spawn(Cow.class);
        when(characters.currentSession(player)).thenReturn(Optional.empty()); PluginData.ownership().ready = false;
        var listener = new TamingListener(plugin);
        player.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT));
        var feed = new PlayerInteractEntityEvent(player, cow, EquipmentSlot.HAND); listener.onInteract(feed);
        assertFalse(feed.isCancelled()); assertNull(player.nextMessage());
        for (String id : List.of("TAMING_ITEM", "COOWNERSHIP_ITEM")) {
            ItemStack token = mmoItem(id); player.getInventory().setItemInMainHand(token);
            var event = new PlayerInteractEntityEvent(player, cow, EquipmentSlot.HAND); listener.onInteract(event);
            assertTrue(event.isCancelled()); assertNotNull(player.nextMessage()); assertEquals(1, token.getAmount());
        }
        assertTrue(PluginData.ownership().rows.isEmpty()); assertNull(cow.getCustomName());
    }
    @Test void deadUnownedAnimalCannotReturnFromFanoutAfterRestartAndHeldAnimalRemains() {
        var player = player(); select(player); Cow cow = spawn(Cow.class);
        FarmAnimal animal = new FarmAnimal(cow.getUniqueId(), 777, cow); UnownedAnimalsManager.addUnownedAnimal(animal);
        PluginData.saveAnimals(); assertTrue(PluginData.ownership().rows.containsKey(cow.getUniqueId()));
        EntityDeathEvent death = mock(EntityDeathEvent.class); when(death.getEntity()).thenReturn(cow);
        new AnimalDeathListener().onAnimalDeath(death);
        assertFalse(PluginData.ownership().rows.containsKey(cow.getUniqueId()));
        PluginData.loadAnimals(); assertFalse(PluginData.getUnownedAnimals().containsKey(cow.getUniqueId()));
        assertFalse(PluginData.ownership().rows.containsKey(cow.getUniqueId()));
        UnownedAnimalsManager.addUnownedAnimal(animal); PluginData.saveAnimals();
        PluginData.ownership().held.put(cow.getUniqueId(), "staff hold"); OwnershipManager.removeAnimal(cow.getUniqueId());
        assertSame(animal, UnownedAnimalsManager.getAnimal(cow.getUniqueId())); assertTrue(PluginData.ownership().rows.containsKey(cow.getUniqueId()));
        PluginData.ownership().held.clear();
    }
    @Test void unchangedOwnershipWithTokenEffectKeepsItsPublicationJournalThroughCompletionAndRestart() throws Exception {
        var player = player(); var session = select(player); Cow cow = spawn(Cow.class); FarmAnimal animal = own(player, cow);
        var store = PluginData.ownership(); var before = new LinkedHashMap<>(store.expected); String previous = store.lastJournalId;
        ItemStack item = mmoItem("COOWNERSHIP_ITEM");
        var intent = AnimalTokenIntent.prepare(player, player, animal, item);
        OwnershipManager.registerOwnership(player.getUniqueId(), animal);
        assertEquals(before, store.expected); assertNotEquals(previous, store.lastJournalId);
        intent.finish(player, player, animal, item, () -> {}); assertEquals(0, item.getAmount());
        var effect = AnimalFiles.object(Files.readString(store.files("ownership/token-effects").getFirst()));
        assertEquals("done", effect.get("status")); assertEquals(store.lastJournalId, effect.get("ownerJournal"));
        var ownerJournal = store.path("ownership/journals/" + effect.get("ownerJournal") + ".json");
        var publication=AnimalFiles.object(Files.readString(ownerJournal));assertEquals(true,publication.get("done"));
        assertEquals(effect.get("id"),publication.get("tokenEffect"));assertEquals(effect.get("id"),store.lastJournalToken);
        var receipts = store.files("ownership/journals"); PluginData.saveAnimals(); assertEquals(receipts, store.files("ownership/journals"));
        PluginData.loadAnimals(); assertTrue(Files.exists(ownerJournal));
        assertTrue(OwnershipManager.isOwner(player.getUniqueId(), cow.getUniqueId()));
        assertEquals(Set.of(session.ref().characterId()), OwnershipManager.getAnimal(cow.getUniqueId()).getOwnerRefs().keySet());
    }
    @Test void removedConfiguredSpeciesKeepKnownRowsAndSpecificHoldsProtected() throws Exception {
        var owner=player();select(owner);Horse horse=spawn(Horse.class);own(owner,horse);
        var stranger=player();select(stranger);new AnimalUtils(new YamlConfiguration());
        var mounts=new MountListener();
        var denied=new PlayerInteractEntityEvent(stranger,horse,EquipmentSlot.HAND);mounts.onInteract(denied);assertTrue(denied.isCancelled());
        var ride=new EntityMountEvent(stranger,horse);mounts.onMount(ride);assertTrue(ride.isCancelled());
        var allowed=new EntityMountEvent(owner,horse);mounts.onMount(allowed);assertFalse(allowed.isCancelled());
        PluginData.ownership().held.put(horse.getUniqueId(),"Malformed stored owner");owner.addAttachment(plugin,"breedingbuddies.use",true);
        var held=new PlayerInteractEntityEvent(owner,horse,EquipmentSlot.HAND);mounts.onInteract(held);assertTrue(held.isCancelled());
        PluginData.ownership().rows.remove(horse.getUniqueId());
        var opaque=new EntityMountEvent(owner,horse);mounts.onMount(opaque);assertTrue(opaque.isCancelled());
        Entity unrelated=mock(Entity.class);when(unrelated.getType()).thenReturn(EntityType.VILLAGER);when(unrelated.getUniqueId()).thenReturn(UUID.randomUUID());
        var ordinary=new PlayerInteractEntityEvent(owner,unrelated,EquipmentSlot.HAND);mounts.onInteract(ordinary);assertFalse(ordinary.isCancelled());
        PluginData.ownership().held.clear();
    }
    @Test void plainSaveWithHeldTokenPrunesSnapshotButPreservesAuthoritativeTokenReceipt() throws Exception {
        var player=player();select(player);Cow cow=spawn(Cow.class);var animal=own(player,cow);var store=PluginData.ownership();
        var token=AnimalTokenIntent.prepare(player,player,animal,mmoItem("TAMING_ITEM"));token.hold(animal);
        Path receipt=store.files("ownership/token-effects").getFirst();String before=Files.readString(receipt);var retained=store.files("ownership/journals");
        PluginData.saveAnimals();assertTrue(store.ready);assertEquals(retained,store.files("ownership/journals"));
        assertFalse(Files.exists(store.path("ownership/journals/"+store.lastJournalId+".json")));assertEquals(before,Files.readString(receipt));
        PluginData.loadAnimals();assertTrue(PluginData.isHeld(cow.getUniqueId()));assertEquals(before,Files.readString(receipt));
    }
}
