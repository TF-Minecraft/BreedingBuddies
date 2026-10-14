package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterRef;
import org.json.simple.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimalOwnershipStoreTest {
    @TempDir Path root;
    final UUID account = UUID.randomUUID(), other = UUID.randomUUID(), id = UUID.randomUUID();
    final CharacterRef a = new CharacterRef(account, UUID.randomUUID()), b = new CharacterRef(other, UUID.randomUUID());
    static JSONObject row(UUID id, UUID... owners) {
        FarmAnimal animal = new FarmAnimal(id,"Daisy",new HashSet<>());
        animal.setState(AnimalStates.HAPPY); animal.setGeneticPoints(341); animal.setFriendshipPoints(999);
        animal.setLastUpdated(new Date(1700000000000L)); animal.setLastRewardCollected(new Date(1700000000100L));
        JSONObject row = AnimalCodec.patch(null,animal); row.remove("characterOwners"); row.remove("heldOwners");
        JSONArray slots = new JSONArray(); for(UUID owner:owners) slots.add(owner.toString()); row.put("ownersUuids",slots);
        JSONObject future = new JSONObject(); future.put("nested",null); row.put("future",future); return row;
    }
    static JSONObject doc(JSONObject... rows) { JSONObject doc=new JSONObject(); JSONArray animals=new JSONArray(); animals.addAll(List.of(rows)); doc.put("animals",animals); doc.put("future-root",null); return doc; }
    void legacy(UUID account,JSONObject... rows) throws IOException { AnimalFiles.write(root.resolve("PlayerData/"+account+".json"),doc(rows).toJSONString()); }
    AnimalOwnershipStore open() throws IOException { var store=new AnimalOwnershipStore(root); store.open(true,k->k.equals(account)?Optional.of(a):k.equals(other)?Optional.of(b):Optional.empty()); return store; }
    @Test void fullInventoryAgreesBeforePublicationAndRawFieldsSurviveRoutineSaveRestart() throws Exception {
        JSONObject source=row(id,account,other); legacy(account,source); legacy(other,source);
        var store=open(); assertTrue(store.ready); assertEquals(Set.of(a,b),AnimalOwnershipStore.owners(store.rows.get(id)));
        assertTrue(AnimalFiles.object(Files.readString(root.resolve("CharacterData/"+a.characterId()+".json"))).containsKey("future-root"));
        FarmAnimal animal=AnimalCodec.load(store.rows.get(id)); animal.setFed(true); animal.increaseFriendship(1); store.save(List.of(animal));
        var reboot=open(); assertEquals(1000L,reboot.rows.get(id).get("friendshipPoints")); assertEquals(true,reboot.rows.get(id).get("fed"));
        assertTrue(((JSONObject)reboot.rows.get(id).get("future")).containsKey("nested")); assertNull(((JSONObject)reboot.rows.get(id).get("future")).get("nested"));
        assertEquals(source,((JSONArray)AnimalFiles.object(Files.readString(root.resolve("PlayerData/"+account+".json"))).get("animals")).getFirst());
        assertEquals(1,reboot.rows.size());
        reboot.forget(id); reboot.save(List.of()); assertTrue(open().rows.isEmpty());
        assertEquals(0,((JSONArray)AnimalFiles.object(Files.readString(root.resolve("CharacterData/"+a.characterId()+".json"))).get("animals")).size());
    }
    @Test void filenameAuthorityAndUnownedCopyDisagreementsAreHeldRatherThanUnioned() throws Exception {
        JSONObject first=row(id,account); legacy(account,first); legacy(other,first);
        var store=open(); assertEquals("Conflicting legacy copies",store.held.get(id)); assertTrue(store.rows.isEmpty()); assertTrue(store.hasOwner(id));
        assertEquals(2,((JSONObject)AnimalFiles.object(Files.readString(root.resolve("ownership/source.json")))).size());
        Path second=root.resolve("second"); AnimalFiles.write(second.resolve("PlayerData/"+account+".json"),doc(first).toJSONString());
        JSONObject changed=AnimalFiles.copy(first); ((JSONObject)changed.get("future")).put("nested","different");
        AnimalFiles.write(second.resolve("unownedanimals.json"),doc(changed).toJSONString());
        var mismatch=new AnimalOwnershipStore(second); mismatch.open(true,k->Optional.of(a)); assertTrue(mismatch.held.containsKey(id));
        Path third=root.resolve("third"); AnimalFiles.write(third.resolve("PlayerData/"+account+".json"),doc(first).toJSONString());
        AnimalFiles.write(third.resolve("unownedanimals.json"),doc(first).toJSONString());
        var agreed=new AnimalOwnershipStore(third); agreed.open(true,k->Optional.of(a)); assertEquals(Set.of(a),AnimalOwnershipStore.owners(agreed.rows.get(id)));
    }
    @Test void reviewedMappingIsFixedWithMissingAndWrongAccountHeirsHeld() throws Exception {
        legacy(account,row(id,account,other)); var store=new AnimalOwnershipStore(root);
        store.open(false,k->{fail("must not resolve before readiness");return Optional.empty();}); assertFalse(store.ready); assertNotNull(store.blocked());
        store.open(true,k->k.equals(account)?Optional.of(a):Optional.of(new CharacterRef(account,b.characterId())));
        assertEquals(Set.of(a),AnimalOwnershipStore.owners(store.rows.get(id))); assertEquals(1,((JSONArray)store.rows.get(id).get("heldOwners")).size());
        store.open(true,k->{fail("fixed published recipient must not be resolved again");return Optional.empty();});
        assertEquals(Set.of(a),AnimalOwnershipStore.owners(store.rows.get(id)));
        UUID stray=UUID.randomUUID(); Path blank=root.resolve("blank"); AnimalFiles.write(blank.resolve("unownedanimals.json"),doc(row(stray)).toJSONString());
        var unowned=new AnimalOwnershipStore(blank); unowned.open(true,k->Optional.empty()); assertFalse(unowned.hasOwner(stray));
    }
    @Test void reviewedHeirsStayFixedWhenTheJournalCannotYetBeCreated() throws Exception {
        legacy(account,row(id,account,other));var store=new AnimalOwnershipStore(root);
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{if(((Path)call.getArgument(0)).toString().contains("ownership/journals"))throw new IOException("journal disk unavailable");return call.callRealMethod();});
            assertThrows(IOException.class,()->store.open(true,k->k.equals(account)?Optional.of(a):Optional.empty()));
        }
        assertTrue(store.files("ownership/journals").isEmpty());assertTrue(Files.exists(root.resolve("ownership/legacy-heirs.json")));
        var reboot=new AnimalOwnershipStore(root);reboot.open(true,k->{fail("fixed reviewed mapping must not be resolved again");return Optional.of(b);});
        assertEquals(Set.of(a),AnimalOwnershipStore.owners(reboot.rows.get(id)));assertEquals(1,((JSONArray)reboot.rows.get(id).get("heldOwners")).size());
    }
    @Test void everyMigrationPublicationCutReplaysTheSameOutputsAndSourcePlan() throws Exception {
        for(int cut=1;cut<=4;cut++) {
            Path caseRoot=root.resolve("cut"+cut); JSONObject source=row(id,account,other);
            AnimalFiles.write(caseRoot.resolve("PlayerData/"+account+".json"),doc(source).toJSONString());
            AnimalFiles.write(caseRoot.resolve("PlayerData/"+other+".json"),doc(source).toJSONString());
            var store=new AnimalOwnershipStore(caseRoot); final int selected=cut; int[] output={0};
            try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
                writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{
                    call.callRealMethod(); Path path=call.getArgument(0);
                    if (!path.toString().contains("ownership/")||path.endsWith("migration.json")) if(++output[0]==selected) throw new IOException("simulated crash after output"); return null;
                });
                assertThrows(IOException.class,()->store.open(true,k->k.equals(account)?Optional.of(a):Optional.of(b)));
            }
            var reboot=new AnimalOwnershipStore(caseRoot); reboot.open(false,k->{fail("must use fixed journal");return Optional.empty();});
            assertTrue(reboot.ready); assertEquals(Set.of(a,b),AnimalOwnershipStore.owners(reboot.rows.get(id))); assertEquals(1,reboot.rows.size());
            JSONObject journal=AnimalFiles.object(Files.readString(reboot.files("ownership/journals").getFirst())); assertEquals(true,journal.get("done"));
            assertEquals(4,((JSONArray)journal.get("outputs")).size());
        }
    }
    @Test void interruptedPlanAndExternalEditsAreHeldWithoutOverwritingAnyOutput() throws Exception {
        legacy(account,row(id,account)); var store=open(); FarmAnimal animal=AnimalCodec.load(store.rows.get(id)); animal.setCared(true);
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{ Path path=call.getArgument(0); if(path.toString().contains("CharacterData")) throw new IOException("disk failed"); return call.callRealMethod(); });
            assertThrows(IOException.class,()->store.save(List.of(animal))); assertFalse(store.ready);
        }
        Path file=root.resolve("CharacterData/"+a.characterId()+".json"); Files.writeString(file,"{\"changed\":true}");
        var reboot=new AnimalOwnershipStore(root); assertThrows(IOException.class,()->reboot.open(true,k->Optional.of(a))); assertFalse(reboot.ready); assertNotNull(reboot.blocked());
        assertEquals("{\"changed\":true}",Files.readString(file));
        assertThrows(IOException.class,()->reboot.commit(Map.of(),new JSONObject()));
        Files.writeString(root.resolve("PlayerData/"+account+".json"),"{}"); assertThrows(IOException.class,reboot::verifySources);
    }
    @Test void repeatedSavesPreserveRawEnvelopesAndEveryConflictingQuarantinedCopy() throws Exception {
        UUID healthy=UUID.randomUUID();legacy(account,row(id,account,other),row(healthy,account,other));legacy(other,row(id,account,other),row(healthy,account,other));
        var store=open();Path first=root.resolve("CharacterData/"+a.characterId()+".json"),second=root.resolve("CharacterData/"+b.characterId()+".json");
        JSONObject document=AnimalFiles.object(Files.readString(first));((JSONObject)((JSONArray)document.get("animals")).stream().filter(value->((JSONObject)value).get("uuid").equals(id.toString())).findFirst().orElseThrow()).put("state","FUTURE_STATE");
        document.put("future-envelope",null);AnimalFiles.write(first,document.toJSONString());
        store=open();assertTrue(store.held.containsKey(id));FarmAnimal live=AnimalCodec.load(store.rows.get(healthy));
        JSONObject retainedFirst=AnimalFiles.object(Files.readString(first)),retainedSecond=AnimalFiles.object(Files.readString(second));
        for(int pass=0;pass<3;pass++){live.increaseFriendship(1);store.save(List.of(live));}
        var reboot=open();assertTrue(reboot.held.containsKey(id));assertEquals(2,reboot.rows.size());
        JSONObject savedFirst=AnimalFiles.object(Files.readString(first)),savedSecond=AnimalFiles.object(Files.readString(second));
        assertTrue(savedFirst.containsKey("future-envelope"));assertNull(savedFirst.get("future-envelope"));assertTrue(savedFirst.containsKey("future-root"));
        for(var pair:List.of(List.of(retainedFirst,savedFirst),List.of(retainedSecond,savedSecond))) {
            Object old=((JSONArray)pair.getFirst().get("animals")).stream().filter(value->((JSONObject)value).get("uuid").equals(id.toString())).findFirst().orElseThrow();
            Object current=((JSONArray)pair.getLast().get("animals")).stream().filter(value->((JSONObject)value).get("uuid").equals(id.toString())).findFirst().orElseThrow();assertEquals(old,current);
        }
    }
    @Test void anEditAfterEarlierPublicationBlocksTheNextDestinationAndNeverOverwritesIt() throws Exception {
        legacy(account,row(id,account,other));legacy(other,row(id,account,other));var store=open();FarmAnimal live=AnimalCodec.load(store.rows.get(id));live.setFed(true);
        var outputs=store.fanout();List<String> files=new ArrayList<>(outputs.keySet());Path first=root.resolve(files.getFirst()),next=root.resolve(files.get(1));
        try(var writes=mockStatic(AnimalFiles.class,CALLS_REAL_METHODS)) {
            writes.when(()->AnimalFiles.write(any(Path.class),anyString())).thenAnswer(call->{call.callRealMethod();if(first.equals(call.getArgument(0)))Files.writeString(next,"{\"manual\":null}");return null;});
            assertThrows(IOException.class,()->store.save(List.of(live)));
        }
        assertEquals("{\"manual\":null}",Files.readString(next));assertNotNull(store.blocked());assertFalse(store.ready);
        var reboot=new AnimalOwnershipStore(root);assertThrows(IOException.class,()->reboot.open(true,k->Optional.of(a)));assertEquals("{\"manual\":null}",Files.readString(next));
    }

    @Test void unchangedSavesDoNotGrowJournalsButRealChangesAndTokenPublicationRetainEvidence() throws Exception {
        legacy(account,row(id,account));var store=open();FarmAnimal animal=AnimalCodec.load(store.rows.get(id));
        store.save(List.of(animal));var receipts=store.files("ownership/journals");String last=store.lastJournalId;
        for(int i=0;i<100;i++)store.save(List.of(animal));
        assertEquals(receipts,store.files("ownership/journals"));assertEquals(last,store.lastJournalId);
        animal.setFed(true);store.save(List.of(animal));assertEquals(receipts,store.files("ownership/journals"));
        assertFalse(Files.exists(store.path("ownership/journals/"+store.lastJournalId+".json")));
        store.tokenEffectId=UUID.randomUUID().toString();store.save(List.of(animal));
        var receipt=AnimalFiles.object(Files.readString(root.resolve("ownership/journals/"+store.lastJournalId+".json")));
        assertEquals(store.tokenEffectId,receipt.get("tokenEffect"));assertEquals(true,receipt.get("done"));
        assertEquals(receipts.size()+1,store.files("ownership/journals").size());
    }
    @Test void unchangedSaveStillRejectsSourceInventoryCurrentBytesAndUnsupportedHolds() throws Exception {
        legacy(account,row(id,account));var store=open();FarmAnimal animal=AnimalCodec.load(store.rows.get(id));store.save(List.of(animal));
        var receipts=store.files("ownership/journals");Path source=root.resolve("PlayerData/"+account+".json");String legacy=Files.readString(source);
        Path extra=root.resolve("CharacterData/"+UUID.randomUUID()+".json");Files.writeString(extra,"{}");
        assertThrows(IOException.class,()->store.save(List.of(animal)));Files.delete(extra);
        Files.writeString(source,legacy+" ");assertThrows(IOException.class,()->store.save(List.of(animal)));Files.writeString(source,legacy);
        Path current=root.resolve("CharacterData/"+a.characterId()+".json");String original=Files.readString(current);
        Files.writeString(current,original+" ");assertThrows(IOException.class,()->store.save(List.of(animal)));Files.writeString(current,original);
        store.held.put(UUID.randomUUID(),"unproved hold");assertThrows(IOException.class,()->store.save(List.of(animal)));
        assertEquals(receipts,store.files("ownership/journals"));
    }
    @Test void invalidFilesSchemasAndArchiveChangesNeverBecomeAnEmptyOwnerSet() throws Exception {
        legacy(account,row(id,account)); var store=open();
        assertThrows(IOException.class,()->store.path("../outside.json")); assertThrows(IOException.class,()->store.path("/outside.json"));
        AnimalFiles.write(root.resolve("PlayerData/"+other+".json"),doc().toJSONString()); assertThrows(IOException.class,store::verifySources);
        Path bad=root.resolve("bad"); AnimalFiles.write(bad.resolve("PlayerData/"+account+".json"),"{bad");
        assertThrows(IOException.class,()->new AnimalOwnershipStore(bad).open(true,k->Optional.of(a)));
        Path missing=root.resolve("missing"); AnimalFiles.write(missing.resolve("PlayerData/"+account+".json"),"{}");
        assertThrows(IOException.class,()->new AnimalOwnershipStore(missing).open(true,k->Optional.of(a)));
        var noPlan=new AnimalOwnershipStore(root.resolve("unplanned")); AnimalFiles.write(noPlan.root.resolve("CharacterData/"+a.characterId()+".json"),doc().toJSONString());
        assertThrows(IOException.class,()->noPlan.open(true,k->Optional.of(a)));
        assertThrows(IOException.class,()->new AnimalOwnershipStore(root.resolve("notready")).save(List.of()));
        store.ready=false; assertThrows(IOException.class,()->store.forget(id)); // deliberately held by explicit readiness below
    }
}
