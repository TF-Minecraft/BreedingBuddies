package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.listeners.TamingListener;
import org.bukkit.entity.Cow;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimalTokenIntentTest extends PluginTestBase {
    ItemStack token(String name){var item=mmoItem("TAMING_ITEM");var meta=item.getItemMeta();meta.setDisplayName(name);item.setItemMeta(meta);ItemUtils.storeDataInItem(item,name,plugin);return item;}
    @Test void knownFailureBeforeOwnershipPublicationNeverConsumesTokenOrRenamesEntity() throws Exception {
        var player=player();Cow cow=spawn(Cow.class);var token=token("Chosen");player.getInventory().setItemInMainHand(token);
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{Path file=call.getArgument(0);if(file.toString().contains("ownership/journals"))throw new IOException("known pre-publication failure");return call.callRealMethod();});
            new TamingListener(plugin).onInteract(new PlayerInteractEntityEvent(player,cow,EquipmentSlot.HAND));
        }
        assertEquals(1,player.getInventory().getItemInMainHand().getAmount());assertNull(cow.getCustomName());assertFalse(PluginData.ownershipReady());
        var receipt=AnimalFiles.object(Files.readString(PluginData.ownership().files("ownership/token-effects").getFirst()));assertEquals("prepared",receipt.get("status"));
        assertEquals(player.getUniqueId().toString(),((JSONObject)receipt.get("recipient")).get("account"));
        var before=ItemStack.deserializeBytes(Base64.getDecoder().decode((String)receipt.get("itemBefore")));assertEquals(token,before);
        PluginData.loadAllData();assertTrue(PluginData.isHeld(cow.getUniqueId()));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void interruptedDebitIsHeldAfterRestartWithoutAutomaticReplayOrRefund() throws Exception {
        var player=player();Cow cow=spawn(Cow.class);player.getInventory().setItemInMainHand(token("Chosen"));
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{Path file=call.getArgument(0);String text=call.getArgument(1);if(file.toString().contains("token-effects")&&text.contains("\"status\":\"done\""))throw new IOException("receipt failure after inventory effect");return call.callRealMethod();});
            new TamingListener(plugin).onInteract(new PlayerInteractEntityEvent(player,cow,EquipmentSlot.HAND));
        }
        assertEquals(0,player.getInventory().getItemInMainHand().getAmount());assertEquals("Chosen",cow.getCustomName());
        JSONObject receipt=AnimalFiles.object(Files.readString(PluginData.ownership().files("ownership/token-effects").getFirst()));assertEquals("debit-prepared",receipt.get("status"));assertNotNull(receipt.get("ownerJournal"));
        JSONObject after=(JSONObject)receipt.get("animalAfter");assertEquals("Chosen",after.get("name"));assertEquals("HAPPY",after.get("state"));
        PluginData.loadAllData();assertTrue(PluginData.isHeld(cow.getUniqueId()));assertFalse(OwnershipManager.isOwner(player.getUniqueId(),cow.getUniqueId()));assertEquals(0,player.getInventory().getItemInMainHand().getAmount());
    }
    @Test void compatibilityTameAndNameHelpersRemainCallableWithCheckedCurrentOwnership() {
        var player=player();Cow cow=spawn(Cow.class);cow.setCustomName("Before");var listener=new TamingListener(plugin);listener.tame(player,cow);
        assertEquals("Before",OwnershipManager.getAnimal(cow.getUniqueId(),player.getUniqueId()).getName());
        var item=token("After");item.setAmount(2);assertTrue(listener.name(cow,item));assertEquals("After",cow.getCustomName());assertEquals(1,item.getAmount());
        when(characters.currentSession(player)).thenReturn(Optional.empty());Cow refused=spawn(Cow.class);listener.tame(player,refused);assertFalse(OwnershipManager.hasOwner(refused.getUniqueId()));
    }

    @Test void changedSessionsOrPayloadsHoldThePreparedEffectBeforeAnyDebit() throws Exception {
        var player=player();Cow cow=spawn(Cow.class);var animal=own(player,cow);var item=token("Chosen");
        var receipt=AnimalTokenIntent.prepare(player,player,animal,item);item.setAmount(2);
        assertThrows(IllegalStateException.class,()->receipt.finish(player,player,animal,item,()->fail("effect must not run")));
        assertEquals(2,item.getAmount());assertTrue(PluginData.isHeld(cow.getUniqueId()));
        PluginData.loadAllData();assertTrue(PluginData.isHeld(cow.getUniqueId()));
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenThrow(new IOException("receipt unavailable"));
            assertThrows(IllegalStateException.class,()->AnimalTokenIntent.prepare(player,player,animal,item));
        }
    }
}
