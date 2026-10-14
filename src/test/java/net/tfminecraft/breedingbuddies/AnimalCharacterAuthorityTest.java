package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.listeners.*;
import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.json.simple.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimalCharacterAuthorityTest extends PluginTestBase {
    CharacterSession select(Player player,UUID character,long generation) { var session=new CharacterSession(new CharacterRef(player.getUniqueId(),character),generation);sessions.put(player.getUniqueId(),session);return session; }
    @Test void sameAccountSwitchCannotCareMountBreedShareRemoveOrViewAnotherCharactersAnimals() throws Exception {
        var player=player();var a=select(player,UUID.randomUUID(),2);Horse horse=spawn(Horse.class);horse.setTamed(true);FarmAnimal animal=own(player,horse);
        var menus=new InventoryManager();menus.openMountMenu(player,horse,animal);var holder=(AnimalInventoryHolder)player.getOpenInventory().getTopInventory().getHolder();
        assertEquals(a,holder.session());horse.addPassenger(player);sessions.remove(player.getUniqueId());
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(player,a,CharacterSessionEndEvent.Reason.SWITCH));assertNull(player.getVehicle());
        var b=select(player,UUID.randomUUID(),3);assertFalse(OwnershipManager.isOwner(player.getUniqueId(),animal.getUuid()));assertTrue(OwnershipManager.getAnimals(player.getUniqueId()).isEmpty());
        var mount=new EntityMountEvent(player,horse);new MountListener().onMount(mount);assertTrue(mount.isCancelled());
        player.openInventory(holder.getInventory());var removal=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,35,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        menus.onInventoryClick(removal);assertTrue(removal.isCancelled());assertEquals(Set.of(a.ref().characterId()),animal.getOwnerRefs().keySet());
        player.getInventory().setItemInMainHand(mmoItem(CustomItems.universalFeed));var care=new PlayerInteractEntityEvent(player,horse,EquipmentSlot.HAND);new AnimalCareListener().onInteract(care);
        assertTrue(care.isCancelled());assertFalse(animal.getFed());
        var tame=new TamingListener(plugin);var token=mmoItem(CustomItems.coownershipItem);ItemUtils.storeDataInItem(token,animal.getUuid().toString(),plugin);
        // Existing bearer-token acquisitions address the NEW character explicitly; the token is not owner authority.
        tame.onRightClick(new PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,token,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND));
        assertEquals(Set.of(a.ref().characterId(),b.ref().characterId()),animal.getOwnerRefs().keySet());assertEquals(0,token.getAmount());
        PluginData.loadAllData();assertTrue(OwnershipManager.isOwner(player.getUniqueId(),animal.getUuid()));
    }
    @Test void staleWrongAccountMissingSessionsAndHeldSlotsCannotGrantTokenOrMenuAuthority() throws Exception {
        var player=player();var neighbour=player();var current=AnimalIdentity.session(player).orElseThrow();Cow cow=spawn(Cow.class);FarmAnimal animal=own(player,cow);
        when(characters.isCurrent(current)).thenReturn(false);assertTrue(AnimalIdentity.session(player).isEmpty());assertFalse(AnimalIdentity.current(current,player));
        assertFalse(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));
        var wrong=AnimalIdentity.session(neighbour).orElseThrow();when(characters.currentSession(player)).thenReturn(Optional.of(wrong));assertTrue(AnimalIdentity.session(player).isEmpty());
        assertThrows(IllegalStateException.class,()->OwnershipManager.registerOwnership(player.getUniqueId(),animal));
        when(characters.currentSession(player)).thenReturn(Optional.empty());var token=mmoItem(CustomItems.coownershipItem);ItemUtils.storeDataInItem(token,cow.getUniqueId().toString(),plugin);
        new TamingListener(plugin).onRightClick(new PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,token,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND));assertEquals(1,token.getAmount());
        when(characters.currentSession(player)).thenReturn(Optional.of(current));when(characters.isCurrent(current)).thenReturn(true);
        JSONObject row=PluginData.ownership().rows.get(cow.getUniqueId());JSONArray held=(JSONArray)row.get("heldOwners");JSONObject slot=new JSONObject();slot.put("account",UUID.randomUUID().toString());slot.put("reason","missing heir");held.add(slot);
        assertTrue(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));assertTrue(OwnershipManager.hasHeldSlots(cow.getUniqueId()));
        assertThrows(IllegalStateException.class,()->OwnershipManager.registerOwnership(neighbour.getUniqueId(),animal));
        OwnershipManager.removeOwnership(player.getUniqueId(),animal);assertTrue(OwnershipManager.hasOwner(cow.getUniqueId()));assertFalse(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));
        assertTrue(AnimalIdentity.session((Player)null).isEmpty());assertFalse(AnimalIdentity.current(null,player));assertFalse(AnimalIdentity.current(current,null));
        ClassLoader absent=new ClassLoader(AnimalIdentity.class.getClassLoader()) { @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException { if(name.startsWith("net.tfminecraft.tlibs.character.estate."))throw new ClassNotFoundException(name);return super.loadClass(name,resolve); } };
        assertFalse(AnimalIdentity.supports(absent,true));assertTrue(AnimalIdentity.supports(absent,false));
        ClassLoader broken=new ClassLoader(AnimalIdentity.class.getClassLoader()) { @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException { if(name.equals("net.tfminecraft.tlibs.character.Characters"))throw new LinkageError("old");return super.loadClass(name,resolve); } };
        assertFalse(AnimalIdentity.supports(broken,false));
    }
    @Test void retainedCompatibilitySavesPreserveCharacterAuthorityAndUnloadableRawRows() throws Exception {
        var player=player();var current=select(player,UUID.randomUUID(),2);Cow cow=spawn(Cow.class);FarmAnimal animal=own(player,cow);
        PluginData.savePlayerData(player.getUniqueId(),Set.of(animal));PluginData.saveAllPlayerData();PluginData.saveUnownedAnimals();
        PluginData.loadPlayerData(player.getUniqueId(),new HashMap<>());PluginData.loadAllPlayerData();PluginData.loadUnownedAnimals();
        assertTrue(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));
        PluginData.ownership().rows.get(cow.getUniqueId()).put("state","FUTURE_STATE");
        java.nio.file.Files.writeString(playerFile(current.ref().characterId()),PluginData.ownership().documents.get("CharacterData/"+current.ref().characterId()+".json").toJSONString());
        PluginData.loadAllData();
        assertTrue(PluginData.isHeld(cow.getUniqueId()));assertNull(OwnershipManager.getAnimal(cow.getUniqueId()));
        PluginData.saveAllData(true);PluginData.loadAllData();assertEquals("FUTURE_STATE",PluginData.ownership().rows.get(cow.getUniqueId()).get("state"));
        assertTrue(OwnershipManager.hasOwner(cow.getUniqueId()));
    }
    @Test void oldHeirApiHoldsAtBootAndSessionStartEjectsUnauthorizedRidersOnly() throws Exception {
        var player=player();Horse horse=spawn(Horse.class);FarmAnimal animal=own(player,horse);var original=AnimalIdentity.session(player).orElseThrow();
        horse.addPassenger(player);server.getPluginManager().callEvent(new CharacterSessionStartEvent(player,original));assertEquals(horse,player.getVehicle());
        var other=select(player,UUID.randomUUID(),3);server.getPluginManager().callEvent(new CharacterSessionStartEvent(player,other));assertNull(player.getVehicle());
        server.getPluginManager().callEvent(new CharacterSessionStartEvent(player,other));
        try(var probe=mockStatic(AnimalIdentity.class,CALLS_REAL_METHODS)) {
            probe.when(()->AnimalIdentity.supports(any(ClassLoader.class),anyBoolean())).thenReturn(false);
            var store=new AnimalOwnershipStore(DATA_DIR.resolve("old-provider"));AnimalIdentity.open(store);assertFalse(store.ready);assertNotNull(store.blocked());
        }
    }

    @Test void deferredMountBreedingCannotChangeNewCharactersAnimalAfterSwitch() {
        var player=player();var a=select(player,UUID.randomUUID(),2);Horse mother=spawn(Horse.class),father=spawn(Horse.class),child=spawn(Horse.class);
        own(player,mother);own(player,father);child.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(7);child.setTamed(false);
        new BreedingListener().onEntityBreed(new EntityBreedEvent(child,mother,father,player,new ItemStack(Material.WHEAT),0));
        select(player,UUID.randomUUID(),3);server.getScheduler().performOneTick();assertFalse(child.isTamed());assertEquals(7,child.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue());
        when(characters.currentSession(player)).thenReturn(Optional.empty());var denied=new EntityBreedEvent(child,mother,father,player,new ItemStack(Material.WHEAT),0);new BreedingListener().onEntityBreed(denied);assertTrue(denied.isCancelled());
    }
}
