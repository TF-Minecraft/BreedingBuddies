package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.json.simple.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnavailableStoreScopeTest extends PluginTestBase {
    @TempDir Path root;
    private CharacterSession select(Player player) {
        var session = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 7L);
        sessions.put(player.getUniqueId(), session);
        return session;
    }
    private void farmTypes(String... types) { var config = new YamlConfiguration(); for (String type : types) config.set(type, Map.of()); new AnimalUtils(config); }
    private PlayerInteractEntityEvent click(Player player, Entity entity) {
        var event = new PlayerInteractEntityEvent(player, entity, EquipmentSlot.HAND); server.getPluginManager().callEvent(event); return event;
    }
    @Test void unavailableStoreLeavesUnrelatedEntitiesAloneAndStillRefusesConfiguredAnimals() {
        var owner = player(); select(owner); var other = player(); select(other); farmTypes("COW");
        Cow cow = spawn(Cow.class); own(owner, cow);
        Villager villager = spawn(Villager.class); ArmorStand stand = spawn(ArmorStand.class);
        PluginData.ownership().ready = false;
        Entity frame=mock(Entity.class),furniture=mock(Entity.class);
        when(frame.getType()).thenReturn(EntityType.ITEM_FRAME);when(frame.getUniqueId()).thenReturn(UUID.randomUUID());
        when(furniture.getType()).thenReturn(EntityType.INTERACTION);when(furniture.getUniqueId()).thenReturn(UUID.randomUUID());
        for (Entity entity : List.of(villager, stand, owner,frame,furniture)) {
            var event = click(other, entity);
            assertFalse(event.isCancelled(), entity.getType().toString()); assertNull(other.nextMessage(), entity.getType().toString());
        }
        assertTrue(click(other, cow).isCancelled());
    }
    @Test void removedSpeciesKnownRowStillRefusesForeignCare() {
        var owner = player(); select(owner); var other = player(); select(other); farmTypes("COW");
        Cow cow = spawn(Cow.class); own(owner, cow); farmTypes("HORSE");
        assertTrue(click(other, cow).isCancelled()); assertNotNull(other.nextMessage());
        assertFalse(click(owner, cow).isCancelled());
    }
    @Test void sessionStartKeepsUnrelatedVehiclesButLeavesAForeignKnownMount() {
        var player = player(); var first = select(player); farmTypes("HORSE");
        for(Entity vehicle:List.of(spawn(Boat.class),spawn(Minecart.class))) {
            assertTrue(vehicle.addPassenger(player));PluginData.ownership().ready=false;
            server.getPluginManager().callEvent(new CharacterSessionStartEvent(player,first));assertEquals(vehicle,player.getVehicle());player.leaveVehicle();
        }
        player.leaveVehicle(); PluginData.ownership().ready = true;
        Horse horse = spawn(Horse.class); own(player, horse); assertTrue(horse.addPassenger(player));
        var second = select(player);
        server.getPluginManager().callEvent(new CharacterSessionStartEvent(player, second));
        assertNull(player.getVehicle());
    }
    @Test void breedingOnlyRequiresReadyOwnershipForConfiguredOrKnownParentsAndStillChecksNeutering() {
        var player=player();select(player);farmTypes("HORSE");
        Pig mother=spawn(Pig.class),father=spawn(Pig.class),child=spawn(Pig.class);PluginData.ownership().ready=false;
        when(characters.currentSession(player)).thenReturn(Optional.empty());
        var vanilla=new EntityBreedEvent(child,mother,father,player,new ItemStack(Material.CARROT),1);server.getPluginManager().callEvent(vanilla);assertFalse(vanilla.isCancelled());
        var configured=new EntityBreedEvent(spawn(Horse.class),spawn(Horse.class),spawn(Horse.class),player,new ItemStack(Material.WHEAT),1);server.getPluginManager().callEvent(configured);assertTrue(configured.isCancelled());
        PluginData.ownership().held.put(mother.getUniqueId(),"Known removed species needs review");
        var known=new EntityBreedEvent(child,mother,father,player,new ItemStack(Material.CARROT),1);server.getPluginManager().callEvent(known);assertTrue(known.isCancelled());PluginData.ownership().held.clear();
        farmTypes();Horse mare=spawn(Horse.class),stallion=spawn(Horse.class);MountUtils.setNeuteredFlag(mare,true);mare.setLoveModeTicks(600);stallion.setLoveModeTicks(600);
        var neutered=new EntityBreedEvent(spawn(Horse.class),mare,stallion,player,new ItemStack(Material.WHEAT),1);server.getPluginManager().callEvent(neutered);assertTrue(neutered.isCancelled());
        assertEquals(0,mare.getLoveModeTicks());assertEquals(0,stallion.getLoveModeTicks());
    }
    @Test void escapePatchesExistingRawRowsAndRetainsUnresolvedLegacySharesAcrossRestart() throws Exception {
        var player=player();var session=select(player);Cow cow=spawn(Cow.class);UUID unresolved=UUID.randomUUID();
        JSONObject row=AnimalOwnershipStoreTest.row(cow.getUniqueId(),player.getUniqueId(),unresolved);
        row.put("futureField","keep-me");row.put("future-null",null);
        AnimalFiles.write(root.resolve("PlayerData/"+player.getUniqueId()+".json"),AnimalOwnershipStoreTest.doc(row).toJSONString());
        var store=new AnimalOwnershipStore(root);store.open(true,account->account.equals(player.getUniqueId())?Optional.of(session.ref()):Optional.empty());
        setStatic(PluginData.class,"ownership",store);PluginData.rebuildAnimals();
        JSONArray held= (JSONArray)AnimalFiles.copy(store.rows.get(cow.getUniqueId())).get("heldOwners");assertEquals(1,held.size());
        FarmAnimal live=OwnershipManager.getAnimal(cow.getUniqueId());live.setDaysOut(100);live.setStableNeeded(true);
        net.tfminecraft.breedingbuddies.listeners.DayChangeListener.globalChangeDay(null,1);PluginData.saveAnimals();
        var reboot=new AnimalOwnershipStore(root);reboot.open(false,account->{fail("fixed migration must not resolve again");return Optional.empty();});setStatic(PluginData.class,"ownership",reboot);PluginData.rebuildAnimals();
        JSONObject after=reboot.rows.get(cow.getUniqueId());assertEquals("ESCAPED",after.get("state"));assertEquals("keep-me",after.get("futureField"));assertTrue(after.containsKey("future-null"));assertNull(after.get("future-null"));
        assertEquals(held,after.get("heldOwners"));assertEquals(0,((JSONArray)after.get("characterOwners")).size());
        assertTrue(OwnershipManager.hasOwner(cow.getUniqueId()));assertTrue(OwnershipManager.hasHeldSlots(cow.getUniqueId()));assertFalse(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));
        var escaped=UnownedAnimalsManager.getAnimal(cow.getUniqueId());assertNotNull(escaped);assertEquals(AnimalStates.ESCAPED,escaped.getState());
        var before=AnimalFiles.copy(after);assertThrows(IllegalStateException.class,()->OwnershipManager.registerOwnership(player.getUniqueId(),escaped));assertEquals(before,reboot.rows.get(cow.getUniqueId()));
        assertEquals(row,((JSONArray)AnimalFiles.object(Files.readString(root.resolve("PlayerData/"+player.getUniqueId()+".json"))).get("animals")).getFirst());
    }
    @Test void ordinaryEscapedAnimalCanBeClaimedAndConfirmedDeathStillForgetsItsRow() throws Exception {
        var first=player();select(first);Cow cow=spawn(Cow.class);var animal=own(first,cow);
        PluginData.ownership().rows.get(cow.getUniqueId()).put("future-null",null);PluginData.ownership().rows.get(cow.getUniqueId()).put("futureField","retained");PluginData.saveAnimals();
        animal.setDaysOut(100);animal.setStableNeeded(true);net.tfminecraft.breedingbuddies.listeners.DayChangeListener.globalChangeDay(null,1);PluginData.saveAnimals();
        assertFalse(OwnershipManager.hasOwner(cow.getUniqueId()));assertEquals(AnimalStates.ESCAPED,UnownedAnimalsManager.getAnimal(cow.getUniqueId()).getState());
        var recipient=player();select(recipient);ItemStack item=mmoItem("TAMING_ITEM");var meta=item.getItemMeta();meta.setDisplayName("Adopted");item.setItemMeta(meta);ItemUtils.storeDataInItem(item,"Adopted",plugin);recipient.getInventory().setItemInMainHand(item);
        assertEquals(1,recipient.getInventory().getItemInMainHand().getAmount());click(recipient,cow);assertEquals(0,recipient.getInventory().getItemInMainHand().getAmount());assertTrue(OwnershipManager.isOwner(recipient.getUniqueId(),cow.getUniqueId()));
        var after=PluginData.ownership().rows.get(cow.getUniqueId());assertEquals("retained",after.get("futureField"));assertTrue(after.containsKey("future-null"));assertNull(after.get("future-null"));
        EntityDeathEvent death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn(cow);when(death.getHandlers()).thenReturn(EntityDeathEvent.getHandlerList());server.getPluginManager().callEvent(death);
        assertFalse(PluginData.ownership().rows.containsKey(cow.getUniqueId()));assertFalse(PluginData.getUnownedAnimals().containsKey(cow.getUniqueId()));
        PluginData.loadAnimals();assertFalse(PluginData.ownership().rows.containsKey(cow.getUniqueId()));assertFalse(OwnershipManager.hasOwner(cow.getUniqueId()));
    }
}
