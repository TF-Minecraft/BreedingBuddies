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

class AnimalEstatesTest extends PluginTestBase {
    @TempDir Path directory;
    final UUID account=UUID.randomUUID(), other=UUID.randomUUID(), death=UUID.randomUUID();
    final CharacterRef from=new CharacterRef(account,UUID.randomUUID()), to=new CharacterRef(other,UUID.randomUUID());
    final CharacterRef coowner=new CharacterRef(UUID.randomUUID(),UUID.randomUUID());
    EstateService service;
    EstateDecision decision(long revision,Recipient recipient) { return new EstateDecision(death,from,Optional.of(Instant.EPOCH),Instant.EPOCH,Optional.of(to),Optional.empty(),Optional.empty(),Recipient.FROZEN,recipient,Recipient.FROZEN,Recipient.SENDER,revision); }
    void activate(EstateDecision decision) {
        if(service==null) { service=mock(EstateService.class);server.getServicesManager().register(EstateService.class,service,plugin,ServicePriority.Normal); }
        when(service.decision(death)).thenReturn(Optional.of(decision));when(service.stateOf(from.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        when(characters.deathId(from.characterId())).thenReturn(Optional.of(death));
        when(characters.info(from.characterId())).thenReturn(Optional.of(new CharacterInfo(from,"A",CharacterStatus.DEAD,null,Set.of(),0,null)));
    }
    AnimalOwnershipStore setup(UUID... animals) throws Exception {
        JSONObject[] rows=Arrays.stream(animals).map(id->AnimalOwnershipStoreTest.row(id,account,other,coowner.ownerId())).toArray(JSONObject[]::new);
        AnimalFiles.write(directory.resolve("PlayerData/"+account+".json"),AnimalOwnershipStoreTest.doc(rows).toJSONString());
        var store=new AnimalOwnershipStore(directory);store.guard=AnimalEstates::guard;store.open(true,k->Optional.of(k.equals(account)?from:k.equals(other)?to:coowner));
        setStatic(PluginData.class,"ownership",store);PluginData.rebuildAnimals();return store;
    }
    @Test void coownerMergeKeepsOneAnimalPhysiologyUnknownFieldsAndCumulativeFrozenWaitingReceipts() throws Exception {
        UUID id=UUID.randomUUID();var store=setup(id);var decision=decision(1,Recipient.character(to));activate(decision);var estate=new AnimalEstates();
        JSONObject before=AnimalFiles.copy(store.rows.get(id));Outcome.Done done=(Outcome.Done)estate.settle(decision);
        assertEquals(List.of(id.toString()),done.movedIds());assertEquals(Set.of(to,coowner),AnimalOwnershipStore.owners(store.rows.get(id)));
        JSONObject after=AnimalFiles.copy(store.rows.get(id));after.put("characterOwners",before.get("characterOwners"));assertEquals(before,after);
        assertEquals(1,store.rows.size());assertEquals(1,((JSONArray)AnimalFiles.object(Files.readString(directory.resolve("CharacterData/"+to.characterId()+".json"))).get("animals")).size());
        assertEquals(0,((JSONArray)AnimalFiles.object(Files.readString(directory.resolve("CharacterData/"+from.characterId()+".json"))).get("animals")).size());
        assertEquals(done,estate.settle(decision));assertEquals(1,estate.describe(to.characterId()).counts().get("animals"));
        for(var kind:List.of(Recipient.WAITING,Recipient.FROZEN)) {var next=decision(2,kind);activate(next);assertEquals(done,estate.settle(next));}
        var same=decision(3,Recipient.character(from));activate(same);assertEquals(done,estate.settle(same));
        assertEquals("breedingbuddies-animals",estate.id());
    }
    @Test void eachEstateFanoutCutRecoversSameDecisionOrHoldsChangedDecisionWithoutOverwriting() throws Exception {
        for(int cut=1;cut<=3;cut++) {
            UUID id=UUID.randomUUID();Path folder=directory.resolve("cut"+cut);Files.createDirectories(folder);
            var store=new AnimalOwnershipStore(folder);JSONObject source=AnimalOwnershipStoreTest.row(id,account,coowner.ownerId());
            AnimalFiles.write(folder.resolve("PlayerData/"+account+".json"),AnimalOwnershipStoreTest.doc(source).toJSONString());
            store.guard=AnimalEstates::guard;store.open(true,k->Optional.of(k.equals(account)?from:coowner));setStatic(PluginData.class,"ownership",store);PluginData.rebuildAnimals();
            var decision=decision(1,Recipient.character(to));activate(decision);final int selected=cut;int[] count={0};boolean[] estateWriting={false};
            try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
                writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{call.callRealMethod();Path file=call.getArgument(0);if(file.toString().contains("ownership/journals")&&((String)call.getArgument(1)).contains("\"kind\":\"estate\""))estateWriting[0]=true;if(estateWriting[0]&&!file.toString().contains("ownership/"))if(++count[0]==selected)throw new IOException("crash after estate file");return null;});
                assertEquals(Outcome.Kind.RETRY,new AnimalEstates().settle(decision).kind());
            }
            var pending=AnimalFiles.object(Files.readString(store.files("ownership/journals").getLast()));assertEquals(false,pending.get("done"));
            assertEquals(4,((JSONArray)pending.get("outputs")).size());
            if(cut==2) {
                var revised=decision(2,Recipient.FROZEN);activate(revised);
                var reboot=new AnimalOwnershipStore(folder);reboot.guard=AnimalEstates::guard;
                assertThrows(IOException.class,()->reboot.open(true,k->Optional.of(to)));setStatic(PluginData.class,"ownership",reboot);
                assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(revised).kind());assertFalse(reboot.ready);
                activate(decision); // explicitly restore the original synthetic decision for the recovery proof
            }
            var reboot=new AnimalOwnershipStore(folder);reboot.guard=AnimalEstates::guard;reboot.open(false,k->{fail("must not resolve mapping again");return Optional.empty();});
            setStatic(PluginData.class,"ownership",reboot);PluginData.rebuildAnimals();
            assertEquals(Set.of(to,coowner),AnimalOwnershipStore.owners(reboot.rows.get(id)));assertEquals(1,reboot.rows.size());
            assertEquals(List.of(id.toString()),((Outcome.Done)new AnimalEstates().settle(decision)).movedIds());
        }
    }
    @Test void realGuardRejectsRevivalRedeathChangedOwnerAndRevisionBeforeMovement() throws Exception {
        UUID id=UUID.randomUUID();var store=setup(id);var decision=decision(1,Recipient.character(to));activate(decision);var estate=new AnimalEstates();
        when(characters.info(from.characterId())).thenReturn(Optional.of(new CharacterInfo(from,"A",CharacterStatus.ALIVE,null,Set.of(),0,null)));assertEquals(Outcome.Kind.RETRY,estate.settle(decision).kind());
        activate(decision);when(characters.deathId(from.characterId())).thenReturn(Optional.of(UUID.randomUUID()));assertEquals(Outcome.Kind.RETRY,estate.settle(decision).kind());
        activate(decision);when(characters.info(from.characterId())).thenReturn(Optional.of(new CharacterInfo(to,"Wrong",CharacterStatus.DEAD,null,Set.of(),0,null)));assertEquals(Outcome.Kind.RETRY,estate.settle(decision).kind());
        activate(decision(2,Recipient.FROZEN));assertEquals(Outcome.Kind.RETRY,estate.settle(decision).kind());assertTrue(AnimalOwnershipStore.owners(store.rows.get(id)).contains(from));
        assertFalse(AnimalEstates.guard(new JSONObject()));
    }
    @Test void hourlyReconcileReportsEachRevisionAndContinuesProviderFailure() throws Exception {
        UUID id=UUID.randomUUID();setup(id);var first=decision(1,Recipient.FROZEN);activate(first);
        when(service.decided()).thenReturn(List.of(first,first));
        doThrow(new IllegalStateException("report temporarily unavailable")).doNothing().when(service).report(eq(death),eq("breedingbuddies-animals"),eq(1L),any());
        server.getScheduler().performTicks(3);verify(service,times(2)).report(eq(death),eq("breedingbuddies-animals"),eq(1L),any());
    }
    @Test void settlementFinishesMatchingPendingPlanBeforeFurtherMovementAndPreservesUnsavedCare() throws Exception {
        UUID id=UUID.randomUUID();var store=setup(id);var decision=decision(1,Recipient.character(to));activate(decision);
        FarmAnimal live=OwnershipManager.getAnimal(id);live.setFed(true);live.setFriendshipPoints(1300);
        boolean[] estateWriting={false};
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{Path file=call.getArgument(0);if(file.toString().contains("ownership/journals")&&((String)call.getArgument(1)).contains("\"kind\":\"estate\""))estateWriting[0]=true;if(estateWriting[0]&&file.toString().contains("CharacterData"))throw new IOException("disk failed");return call.callRealMethod();});
            assertEquals(Outcome.Kind.RETRY,new AnimalEstates().settle(decision).kind());
        }
        assertEquals(Outcome.Kind.DONE,new AnimalEstates().settle(decision).kind());assertTrue(store.ready);
        assertEquals(true,store.rows.get(id).get("fed"));assertEquals(1300L,store.rows.get(id).get("friendshipPoints"));
        var plan=AnimalFiles.object(Files.readString(store.files("ownership/journals").getLast()));assertEquals(true,plan.get("done"));
    }

    @Test void inheritanceKeepsUnrelatedBufferedPhysiologyAndRejectsAChangedDecisionAfterFlush() throws Exception {
        UUID id=UUID.randomUUID(),unrelated=UUID.randomUUID();var store=setup(id);var decision=decision(1,Recipient.character(to));activate(decision);
        JSONObject extra=AnimalOwnershipStoreTest.row(unrelated);JSONArray slots=new JSONArray();slots.add(AnimalOwnershipStore.ref(to));extra.put("characterOwners",slots);extra.put("heldOwners",new JSONArray());store.rows.put(unrelated,extra);store.commit(store.fanout(),new JSONObject());store.load();PluginData.rebuildAnimals();
        FarmAnimal recipientAnimal=OwnershipManager.getAnimal(unrelated);recipientAnimal.setFed(true);recipientAnimal.setFriendshipPoints(1750);
        assertEquals(Outcome.Kind.DONE,new AnimalEstates().settle(decision).kind());assertEquals(true,store.rows.get(unrelated).get("fed"));assertEquals(1750L,store.rows.get(unrelated).get("friendshipPoints"));
        assertEquals(2,store.rows.size());assertEquals(Set.of(to,coowner),AnimalOwnershipStore.owners(store.rows.get(id)));
        UUID late=UUID.randomUUID();JSONObject row=AnimalOwnershipStoreTest.row(late);JSONArray source=new JSONArray();source.add(AnimalOwnershipStore.ref(from));row.put("characterOwners",source);row.put("heldOwners",new JSONArray());store.rows.put(late,row);store.commit(store.fanout(),new JSONObject());store.load();PluginData.rebuildAnimals();
        OwnershipManager.getAnimal(late).setFed(true);
        var next=decision(2,Recipient.character(to));activate(next);boolean[] flushed={false};
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{call.callRealMethod();String text=call.getArgument(1);if(text.contains("\"kind\":\"gameplay\"")&&text.contains("\"done\":true")){activate(decision(3,Recipient.FROZEN));flushed[0]=true;}return null;});
            assertEquals(Outcome.Kind.RETRY,new AnimalEstates().settle(next).kind());
        }
        assertTrue(flushed[0]);assertTrue(AnimalOwnershipStore.owners(store.rows.get(late)).contains(from));
    }

    @Test void historicalHeldTokenEvidenceSurvivesReviewedCompletionCareAndRestart() throws Exception {
        UUID inherited=UUID.randomUUID(),heldAnimal=UUID.randomUUID();var store=setup(inherited);
        JSONObject extra=AnimalOwnershipStoreTest.row(heldAnimal);JSONArray owners=new JSONArray();owners.add(AnimalOwnershipStore.ref(to));extra.put("characterOwners",owners);extra.put("heldOwners",new JSONArray());
        store.rows.put(heldAnimal,extra);store.commit(store.fanout(),new JSONObject());store.load();PluginData.rebuildAnimals();
        var player=player();FarmAnimal live=OwnershipManager.getAnimal(heldAnimal);var token=AnimalTokenIntent.prepare(player,player,live,mmoItem("TAMING_ITEM"));token.hold(live);
        var first=decision(1,Recipient.character(to));activate(first);var estate=new AnimalEstates();Outcome.Done receipt=(Outcome.Done)estate.settle(first);assertEquals(List.of(inherited.toString()),receipt.movedIds());
        Path estatePath=store.files("ownership/journals").getLast();JSONObject frozen=AnimalFiles.object(Files.readString(estatePath));assertEquals(1,((JSONObject)frozen.get("tokenHolds")).size());
        Path effectPath=store.files("ownership/token-effects").getFirst();JSONObject effect=AnimalFiles.object(Files.readString(effectPath));effect.put("status","done");effect.put("reviewedResolution","synthetic staff reconciliation");AnimalFiles.write(effectPath,effect.toJSONString());
        store.load();PluginData.rebuildAnimals();live=OwnershipManager.getAnimal(heldAnimal);assertNotNull(live);live.setFed(true);live.setFriendshipPoints(1875);PluginData.saveAnimals();
        assertEquals(receipt,estate.settle(first));var waiting=decision(2,Recipient.WAITING);activate(waiting);assertEquals(receipt,estate.settle(waiting));
        var reboot=new AnimalOwnershipStore(directory);reboot.guard=AnimalEstates::guard;reboot.open(false,k->{fail("must not resolve reviewed mapping again");return Optional.empty();});setStatic(PluginData.class,"ownership",reboot);PluginData.rebuildAnimals();
        assertEquals(receipt,estate.settle(waiting));assertEquals(true,reboot.rows.get(heldAnimal).get("fed"));assertEquals(1875L,reboot.rows.get(heldAnimal).get("friendshipPoints"));assertEquals(Set.of(to,coowner),AnimalOwnershipStore.owners(reboot.rows.get(inherited)));
        JSONObject altered=AnimalFiles.object(Files.readString(estatePath));JSONObject snapshot=(JSONObject)((JSONObject)altered.get("tokenHolds")).values().iterator().next();snapshot.put("actor",AnimalOwnershipStore.ref(from));AnimalFiles.write(estatePath,altered.toJSONString());
        var unchanged=new LinkedHashMap<String,String>(reboot.expected);assertEquals(Outcome.Kind.BLOCKED,estate.settle(waiting).kind());assertEquals(unchanged,reboot.expected);assertNotNull(reboot.blocked());
    }
    @Test void restoredAlreadyInheritedSourceIsBlockedForSameAndNewRevision() throws Exception {
        UUID id=UUID.randomUUID();var store=setup(id);JSONObject original=AnimalFiles.copy(store.rows.get(id));var first=decision(1,Recipient.character(to));activate(first);
        assertEquals(Outcome.Kind.DONE,new AnimalEstates().settle(first).kind());
        store.rows.put(id,original);store.commit(store.fanout(),new JSONObject());store.load();PluginData.rebuildAnimals();
        var bytes=new LinkedHashMap<String,String>(store.expected);
        for(var next:List.of(first,decision(2,Recipient.character(to)),decision(3,Recipient.FROZEN))) {
            activate(next);assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(next).kind());assertEquals(bytes,store.expected);assertTrue(AnimalOwnershipStore.owners(store.rows.get(id)).contains(from));
        }
    }
    @Test void runtimeFailureBeforeEstatePublicationRestoresOriginalRowsAndDoesNotClaimMovement() throws Exception {
        UUID id=UUID.randomUUID();var store=setup(id);var d=decision(1,Recipient.character(to));activate(d);
        PluginData.saveAnimals();
        var before=new LinkedHashMap<>(store.rows);var documents=new LinkedHashMap<>(store.expected);
        try(var copies=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            copies.when(()->AnimalFiles.copy(any(JSONObject.class))).thenAnswer(call->{
                JSONObject value=call.getArgument(0);if("estate".equals(value.get("kind")))throw new IllegalStateException("estate metadata copy failed");
                return call.callRealMethod();
            });
            assertEquals(Outcome.Kind.BLOCKED,new AnimalEstates().settle(d).kind());
        }
        assertEquals(before,store.rows);assertEquals(documents,store.expected);assertFalse(store.ready);
        assertTrue(AnimalOwnershipStore.owners(store.rows.get(id)).contains(from));
        for(var path:store.files("ownership/journals"))assertFalse(AnimalFiles.object(Files.readString(path)).containsKey("estate"));
    }
    @Test void heldRowsAndRecipientConflictsNeverPartiallyModifyOtherSlots() throws Exception {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();var store=setup(first,second);var d=decision(1,Recipient.character(to));activate(d);var estate=new AnimalEstates();
        store.held.put(second,"Conflict");assertEquals(Outcome.Kind.BLOCKED,estate.settle(d).kind());assertTrue(AnimalOwnershipStore.owners(store.rows.get(first)).contains(from));
        assertTrue(estate.describe(from.characterId()).labels().stream().anyMatch(s->s.contains("held")));
        store.held.clear();JSONArray owners=(JSONArray)store.rows.get(second).get("characterOwners");owners.add(AnimalOwnershipStore.ref(new CharacterRef(UUID.randomUUID(),to.characterId())));
        assertEquals(Outcome.Kind.BLOCKED,estate.settle(d).kind());assertTrue(AnimalOwnershipStore.owners(store.rows.get(first)).contains(from));
        setStatic(PluginData.class,"ownership",null);assertEquals(Holdings.NONE,estate.describe(from.characterId()));assertEquals(Outcome.Kind.RETRY,estate.settle(d).kind());
    }
}
