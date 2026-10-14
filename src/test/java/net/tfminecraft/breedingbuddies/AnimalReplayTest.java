package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.estate.*;
import org.bukkit.plugin.ServicePriority;
import org.json.simple.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimalReplayTest extends PluginTestBase {
    @TempDir Path directory;
    final UUID account=UUID.randomUUID(),other=UUID.randomUUID(),animal=UUID.randomUUID(),unrelated=UUID.randomUUID(),death=UUID.randomUUID();
    final CharacterRef from=new CharacterRef(account,UUID.randomUUID()),to=new CharacterRef(other,UUID.randomUUID()),third=new CharacterRef(UUID.randomUUID(),UUID.randomUUID());
    EstateDecision decision;
    AnimalOwnershipStore pending(Path root) throws Exception {
        AnimalFiles.write(root.resolve("PlayerData/"+account+".json"),AnimalOwnershipStoreTest.doc(AnimalOwnershipStoreTest.row(animal,account,other)).toJSONString());
        AnimalFiles.write(root.resolve("PlayerData/"+other+".json"),AnimalOwnershipStoreTest.doc(AnimalOwnershipStoreTest.row(animal,account,other),AnimalOwnershipStoreTest.row(unrelated,other)).toJSONString());
        var store=new AnimalOwnershipStore(root);store.guard=AnimalEstates::guard;store.open(true,k->Optional.of(k.equals(account)?from:to));setStatic(PluginData.class,"ownership",store);PluginData.rebuildAnimals();
        decision=new EstateDecision(death,from,Optional.of(Instant.EPOCH),Instant.EPOCH,Optional.of(to),Optional.empty(),Optional.empty(),Recipient.FROZEN,Recipient.character(to),Recipient.FROZEN,Recipient.SENDER,1);
        var service=mock(EstateService.class);server.getServicesManager().register(EstateService.class,service,plugin,ServicePriority.Highest);
        when(service.decision(death)).thenReturn(Optional.of(decision));when(service.stateOf(from.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(characters.info(from.characterId())).thenReturn(Optional.of(new CharacterInfo(from,"Source",CharacterStatus.DEAD,null,Set.of(),0,null)));when(characters.deathId(from.characterId())).thenReturn(Optional.of(death));
        boolean[] armed={false};
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{Path file=call.getArgument(0);String text=call.getArgument(1);if(file.toString().contains("ownership/journals")&&text.contains("\"kind\":\"estate\""))armed[0]=true;if(armed[0]&&!file.toString().contains("ownership/"))throw new IOException("cut before estate publication");return call.callRealMethod();});
            assertEquals(Outcome.Kind.RETRY,new AnimalEstates().settle(decision).kind());
        }
        return store;
    }
    Map<Path,String> data(AnimalOwnershipStore store) throws IOException {Map<Path,String> data=new HashMap<>();for(Path file:store.files("CharacterData"))data.put(file,Files.readString(file));data.put(store.root.resolve("character-unowned.json"),Files.readString(store.root.resolve("character-unowned.json")));return data;}
    @Test void hashConsistentValidJsonTamperingCannotChangeRecipientAnimalRosterPhysiologyOrPath() throws Exception {
        for(String fault:List.of("recipient","animal","care","roster","path","version","kind","relabeled","held")) {
            var store=pending(directory.resolve(fault));var untouched=data(store);Path path=store.files("ownership/journals").getLast();JSONObject journal=AnimalFiles.object(Files.readString(path));JSONObject canonical=(JSONObject)journal.get("canonical");
            for(Object value:(JSONArray)journal.get("outputs")) {
                JSONObject output=(JSONObject)value;if(!("CharacterData/"+to.characterId()+".json").equals(output.get("path")))continue;
                JSONObject after=AnimalFiles.object((String)output.get("after"));
                if(fault.equals("recipient")) {
                    for(Object row:(JSONArray)after.get("animals"))if(animal.toString().equals(((JSONObject)row).get("uuid"))) {JSONArray owners=new JSONArray();owners.add(AnimalOwnershipStore.ref(third));((JSONObject)row).put("characterOwners",owners);((JSONObject)canonical.get(animal.toString())).put("characterOwners",owners);}
                } else if(fault.equals("animal")) {
                    JSONObject changed=(JSONObject)canonical.remove(animal.toString());changed.put("uuid",UUID.randomUUID().toString());canonical.put(changed.get("uuid"),changed);
                    for(Object row:(JSONArray)after.get("animals"))if(animal.toString().equals(((JSONObject)row).get("uuid")))((JSONObject)row).put("uuid",changed.get("uuid"));
                } else if(fault.equals("care")) {
                    ((JSONObject)canonical.get(unrelated.toString())).put("fed",true);for(Object row:(JSONArray)after.get("animals"))if(unrelated.toString().equals(((JSONObject)row).get("uuid")))((JSONObject)row).put("fed",true);
                } else if(fault.equals("roster")) {((JSONArray)after.get("animals")).removeIf(item -> unrelated.toString().equals(((JSONObject)item).get("uuid")));}
                else if(fault.equals("path")) {output.put("path","CharacterData/"+third.characterId()+".json");output.put("before",null);after.put("owner",AnimalOwnershipStore.ref(third));}
                output.put("after",after.toJSONString());output.put("afterSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(after.toJSONString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            }
            if(fault.equals("held")){((JSONObject)journal.get("held")).put(animal.toString(),"invented quarantine");canonical.remove(animal.toString());((JSONArray)journal.get("animals")).clear();}
            if(fault.equals("relabeled"))journal.put("kind","gameplay");
            if(fault.equals("version"))journal.put("format",2L);if(fault.equals("kind"))journal.put("kind","future-kind");AnimalFiles.write(path,journal.toJSONString());
            assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(decision).kind(),fault);assertNotNull(store.blocked());assertFalse(store.ready);
            assertEquals(untouched,data(store),fault);assertEquals(false,AnimalFiles.object(Files.readString(path)).get("done"));
        }
    }
    @Test void completedReceiptCorruptionIsBlockedBeforeUsingCumulativeIds() throws Exception {
        for(String fault:List.of("recipient","ids","care","relabeled","consistent-recipient")) {
            var store=pending(directory.resolve("done-"+fault));assertEquals(Outcome.Kind.DONE,new AnimalEstates().settle(decision).kind());
            var untouched=data(store);Path path=store.files("ownership/journals").getLast();JSONObject journal=AnimalFiles.object(Files.readString(path));
            if(fault.equals("recipient"))((JSONObject)journal.get("estate")).put("to",AnimalOwnershipStore.ref(third));
            if(fault.equals("consistent-recipient")) {
                ((JSONObject)journal.get("estate")).put("to",AnimalOwnershipStore.ref(third));
                // Even a mutually consistent tampered header/canonical/output must match the captured decision.
                JSONObject changed=(JSONObject)((JSONObject)journal.get("canonical")).get(animal.toString());JSONArray slots=new JSONArray();slots.add(AnimalOwnershipStore.ref(third));changed.put("characterOwners",slots);
                for(Object item:(JSONArray)journal.get("outputs")) {
                    JSONObject output=(JSONObject)item;JSONObject after=AnimalFiles.object((String)output.get("after"));
                    ((JSONArray)after.get("animals")).removeIf(row->animal.toString().equals(((JSONObject)row).get("uuid")));
                    if(("CharacterData/"+to.characterId()+".json").equals(output.get("path"))) {
                        // Existing recipient unrelated rows remain in place; this path is deliberately forbidden.
                        after.put("owner",AnimalOwnershipStore.ref(third));output.put("path","CharacterData/"+third.characterId()+".json");output.put("before",null);((JSONArray)after.get("animals")).add(changed);
                    }
                    output.put("after",after.toJSONString());
                }
            }
            if(fault.equals("ids"))((JSONArray)journal.get("animals")).add(UUID.randomUUID().toString());
            if(fault.equals("relabeled"))journal.put("kind","gameplay");
            if(fault.equals("care"))((JSONObject)((JSONObject)journal.get("canonical")).get(unrelated.toString())).put("fed",true);
            AnimalFiles.write(path,journal.toJSONString());
            assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(decision).kind(),fault);assertEquals(untouched,data(store));assertNotNull(store.blocked());
        }
    }
    @Test void duplicateOwnershipAndNestedUnknownKeysHoldOriginalBytes() throws Exception {
        for(String fault:List.of("owners","unknown")) {
            Path root=directory.resolve(fault),source=root.resolve("PlayerData/"+account+".json");String json=AnimalOwnershipStoreTest.doc(AnimalOwnershipStoreTest.row(animal,account)).toJSONString();
            json=fault.equals("owners")?json.replace("\"ownersUuids\":[","\"ownersUuids\":[],\"ownersUuids\":["):json.replace("\"nested\":null","\"nested\":null,\"nested\":\"different\"");
            AnimalFiles.write(source,json);String original=json;var store=new AnimalOwnershipStore(root);assertThrows(IOException.class,()->store.open(true,k->Optional.of(from)));assertFalse(store.ready);assertEquals(original,Files.readString(source));assertFalse(Files.exists(root.resolve("ownership/migration.json")));
        }
        var store=pending(directory.resolve("after-duplicate"));var untouched=data(store);Path path=store.files("ownership/journals").getLast();JSONObject journal=AnimalFiles.object(Files.readString(path));
        JSONObject output=(JSONObject)((JSONArray)journal.get("outputs")).stream().filter(value -> ((String)((JSONObject)value).get("after")).contains("\"nested\":null")).findFirst().orElseThrow();output.put("after",((String)output.get("after")).replace("\"nested\":null","\"nested\":null,\"nested\":false"));AnimalFiles.write(path,journal.toJSONString());
        assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(decision).kind());assertEquals(untouched,data(store));
    }
}
