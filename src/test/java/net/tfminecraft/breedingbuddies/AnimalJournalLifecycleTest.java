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

class AnimalJournalLifecycleTest {
    @TempDir Path root;
    final UUID account = UUID.randomUUID(), id = UUID.randomUUID();
    final CharacterRef owner = new CharacterRef(account, UUID.randomUUID());
    AnimalOwnershipStore open() throws IOException {
        var store = new AnimalOwnershipStore(root);
        store.open(true, key -> Optional.of(owner));
        return store;
    }
    AnimalOwnershipStore setup(boolean animal) throws IOException {
        AnimalFiles.write(root.resolve("PlayerData/" + account + ".json"),
            AnimalOwnershipStoreTest.doc(animal ? new JSONObject[]{AnimalOwnershipStoreTest.row(id, account)} : new JSONObject[0]).toJSONString());
        return open();
    }
    Map<String, String> snapshot() throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        }
        return result;
    }
    @Test void idleAutosavesWriteNothingAndDoNotReadRetainedJournalHistory() throws Exception {
        var store = setup(true); var animal = AnimalCodec.load(store.rows.get(id));
        store.save(List.of(animal)); var before = snapshot(); var expected = new LinkedHashMap<>(store.expected); String last = store.lastJournalId;
        try (var reads = mockStatic(AnimalFiles.class, CALLS_REAL_METHODS)) {
            String history = Files.readString(store.files("ownership/journals").getFirst());
            reads.when(() -> AnimalFiles.object(history)).thenThrow(new IOException("save must not parse journal history"));
            for (int i = 0; i < 2; i++) store.save(List.of(animal));
        }
        assertEquals(before, snapshot()); assertEquals(expected, store.expected); assertEquals(last, store.lastJournalId);
        assertEquals(store.fanout(), store.documents); assertTrue(store.ready);
        assertTrue(snapshot().keySet().stream().noneMatch(name -> name.endsWith(".next")));
    }
    @Test void omittedEmptyOwnerOutputCannotBeMistakenForANoop() throws Exception {
        var store = setup(false); var before = snapshot(); var expected = new LinkedHashMap<>(store.expected);
        var outputs = store.fanout(); outputs.remove("CharacterData/" + owner.characterId() + ".json");
        assertThrows(IOException.class, () -> store.commit(outputs, new JSONObject()));
        assertEquals(before, snapshot()); assertEquals(expected, store.expected);
    }
    @Test void changedSavesPruneOnlyTheirOwnDoneGameplayJournalAndRetainTokenHistory() throws Exception {
        var store = setup(true); var animal = AnimalCodec.load(store.rows.get(id)); store.save(List.of(animal));
        store.tokenEffectId = UUID.randomUUID().toString(); store.save(List.of(animal)); store.tokenEffectId = null;
        var retained = snapshot(); var history = store.files("ownership/journals");
        assertEquals(2, history.size());
        animal.setFed(true); store.save(List.of(animal));
        assertEquals(history, store.files("ownership/journals")); assertFalse(Files.exists(store.path("ownership/journals/" + store.lastJournalId + ".json")));
        for (String name : retained.keySet()) if (name.startsWith("ownership/")) assertEquals(retained.get(name), snapshot().get(name), name);
        assertEquals(true, store.rows.get(id).get("fed")); assertEquals(true, open().rows.get(id).get("fed"));
    }
    @Test void interruptedGameplayPublicationReplaysAndPrunesOnlyAfterDurableCompletion() throws Exception {
        var store = setup(true); var animal = AnimalCodec.load(store.rows.get(id)); animal.setCared(true);
        try (var writes = mockStatic(AnimalFiles.class, CALLS_REAL_METHODS)) {
            writes.when(() -> AnimalFiles.write(any(Path.class), anyString())).thenAnswer(call -> {
                if (((Path) call.getArgument(0)).toString().contains("CharacterData")) throw new IOException("publication interrupted");
                return call.callRealMethod();
            });
            assertThrows(IOException.class, () -> store.save(List.of(animal))); assertFalse(store.ready);
        }
        var pending = store.files("ownership/journals").getLast(); var plan = AnimalFiles.object(Files.readString(pending)); assertEquals(false, plan.get("done"));
        var before = snapshot(); assertThrows(IOException.class, () -> store.save(List.of(animal))); assertEquals(before, snapshot());
        var reboot = open(); assertTrue(reboot.ready); assertFalse(Files.exists(pending)); assertEquals(true, reboot.rows.get(id).get("cared"));
        for (Object value : (JSONArray) plan.get("outputs")) { var output = (JSONObject) value; assertEquals(output.get("after"), Files.readString(root.resolve((String) output.get("path")))); }
    }
    @Test void leftoverDoneGameplayIsInertOnOpenAndIsNeverSweptByLaterSaves() throws Exception {
        var store = setup(true); var animal = AnimalCodec.load(store.rows.get(id));
        String[] done = {null}; Path[] path = {null};
        try (var writes = mockStatic(AnimalFiles.class, CALLS_REAL_METHODS)) {
            writes.when(() -> AnimalFiles.write(any(Path.class), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                if (text.contains("\"kind\":\"gameplay\"") && text.contains("\"done\":true")) { done[0] = text; path[0] = call.getArgument(0); }
                return call.callRealMethod();
            });
            animal.setFed(true); store.save(List.of(animal));
        }
        Files.writeString(path[0], done[0]); var before = snapshot(); var reboot = open(); assertTrue(reboot.ready); assertEquals(before, snapshot());
        animal.setCared(true); reboot.save(List.of(animal)); assertEquals(done[0], Files.readString(path[0]));
        assertEquals(2, reboot.files("ownership/journals").size());
    }
    @Test void noopRefusesReceiptPathConflictsWithoutTouchingAnyFile() throws Exception {
        var store = setup(true); var animal = AnimalCodec.load(store.rows.get(id)); store.save(List.of(animal));
        JSONObject effect = new JSONObject(); effect.put("id", UUID.randomUUID().toString()); effect.put("animal", id.toString()); effect.put("status", "prepared");
        AnimalFiles.write(root.resolve("ownership/token-effects/" + UUID.randomUUID() + ".json"), effect.toJSONString()); store.held.put(id, "Unfinished token effect");
        var before = snapshot(); assertThrows(IOException.class, () -> store.save(List.of(animal))); assertEquals(before, snapshot());
    }
    @Test void pruningUnlinkAndForceFailuresCannotInvalidateCompletedPublication() throws Exception {
        var store = spy(setup(true)); var animal = AnimalCodec.load(store.rows.get(id));
        doThrow(new IOException("unlink unavailable")).when(store).prune(any(Path.class));
        animal.setFed(true); assertDoesNotThrow(() -> store.save(List.of(animal))); assertTrue(store.ready);
        Path inert = store.path("ownership/journals/" + store.lastJournalId + ".json");
        assertEquals(true, AnimalFiles.object(Files.readString(inert)).get("done")); String original = Files.readString(inert);
        doAnswer(call -> { Files.deleteIfExists(call.getArgument(0)); throw new IOException("directory force unavailable"); }).when(store).prune(any(Path.class));
        animal.setCared(true); assertDoesNotThrow(() -> store.save(List.of(animal))); assertTrue(store.ready);
        assertFalse(Files.exists(store.path("ownership/journals/" + store.lastJournalId + ".json"))); assertEquals(original, Files.readString(inert));
        var reboot = open(); assertTrue(reboot.ready); assertEquals(true, reboot.rows.get(id).get("fed")); assertEquals(true, reboot.rows.get(id).get("cared"));
    }
    @Test void firstAcquisitionCreatesNewOwnerAndLaterFanoutRetainsEmptiedOwners() throws Exception {
        var store = setup(false); var next = new CharacterRef(account, UUID.randomUUID());
        JSONObject row = AnimalOwnershipStoreTest.row(id); JSONArray slots = new JSONArray(); slots.add(AnimalOwnershipStore.ref(next));
        row.put("characterOwners", slots); row.put("heldOwners", new JSONArray()); store.rows.put(id, row);
        store.commit(store.fanout(), new JSONObject()); store.load();
        assertEquals(Set.of(next), AnimalOwnershipStore.owners(store.rows.get(id))); assertTrue(store.ready);
        slots = new JSONArray(); slots.add(AnimalOwnershipStore.ref(owner)); store.rows.get(id).put("characterOwners", slots);
        store.commit(store.fanout(), new JSONObject()); store.load();
        String previous = "CharacterData/" + next.characterId() + ".json";
        assertTrue(store.expected.containsKey(previous)); assertEquals(0, ((JSONArray) AnimalFiles.object(Files.readString(root.resolve(previous))).get("animals")).size());
        assertEquals(Set.of(owner), AnimalOwnershipStore.owners(open().rows.get(id))); assertEquals(1, store.files("ownership/journals").size());
    }
}
