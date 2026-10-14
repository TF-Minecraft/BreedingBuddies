package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterRef;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Character file fanout; journals are recovery evidence, not a second animal database. */
final class AnimalOwnershipStore {
    final Path root;
    final Map<UUID, JSONObject> rows = new LinkedHashMap<>();
    final Map<UUID, String> held = new LinkedHashMap<>();
    final Map<String, JSONObject> documents = new LinkedHashMap<>();
    final Map<String, String> expected = new LinkedHashMap<>();
    Predicate<JSONObject> guard = ignored -> false;
    boolean ready;
    String tokenEffectId, lastJournalId;
    private String blocked;
    AnimalOwnershipStore(Path root) { this.root = root; }
    Path path(String relative) throws IOException {
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root.normalize()) || Path.of(relative).isAbsolute()) throw new IOException("Unsafe journal path");
        return result;
    }
    List<Path> files(String directory) throws IOException {
        Path folder = path(directory);
        if (!Files.exists(folder)) return List.of();
        try (var stream = Files.list(folder)) { return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList(); }
    }
    String relative(Path path) { return root.relativize(path).toString(); }
    void open(boolean heirsReady, Function<UUID, Optional<CharacterRef>> heirs) throws IOException {
        ready = false; blocked = null;
        verifySources();
        recover();
        if (!Files.exists(path("ownership/migration.json"))) {
            if (!heirsReady) { blocked = "Reviewed legacy heirs are unavailable"; return; }
            migrate(heirs);
        }
        load();
        ready = true;
    }
    void recover() throws IOException {
        for (Path journalPath : files("ownership/journals")) {
            JSONObject journal = AnimalFiles.object(Files.readString(journalPath));
            if (!Boolean.TRUE.equals(journal.get("done"))) publish(journalPath, journal);
        }
    }
    String blocked() { return blocked; }
    void verifySources() throws IOException {
        Path archive = path("ownership/source.json");
        if (!Files.exists(archive)) return;
        JSONObject sources = AnimalFiles.object(Files.readString(archive));
        Set<String> actual = new HashSet<>();
        for (Path file : files("PlayerData")) actual.add(relative(file));
        if (Files.exists(path("unownedanimals.json"))) actual.add("unownedanimals.json");
        if (!actual.equals(sources.keySet())) throw new IOException("Legacy file inventory changed after archive");
        for (Object key : sources.keySet()) if (!Objects.equals(AnimalFiles.read(path((String) key)), sources.get(key))) throw new IOException("Legacy source changed after plan: " + key);
    }
    void validateReceipt(Path journalPath, JSONObject journal) throws IOException {
        try { AnimalReplay.validate(this, journalPath, journal); }
        catch (Exception failure) { blocked = "Invalid animal replay: " + failure.getMessage(); throw new IOException(blocked, failure); }
    }
    void publish(Path journalPath, JSONObject journal) throws IOException {
        validateReceipt(journalPath, journal);
        verifySources();
        JSONArray outputs = (JSONArray) journal.get("outputs");
        for (Object item : outputs) {
            JSONObject output = (JSONObject) item;
            String actual = AnimalFiles.read(path((String) output.get("path")));
            if (!Objects.equals(actual, output.get("before")) && !Objects.equals(actual, output.get("after"))) {
                blocked = "Conflicting animal output " + output.get("path"); throw new IOException(blocked);
            }
        }
        for (Object item : outputs) {
            if (journal.containsKey("estate") && !guard.test(journal)) {
                blocked = "Stale or unavailable estate decision " + journal.get("id"); throw new IOException(blocked);
            }
            JSONObject output = (JSONObject) item;
            Path destination = path((String) output.get("path"));
            String actual = AnimalFiles.read(destination);
            if (!Objects.equals(actual, output.get("before")) && !Objects.equals(actual, output.get("after"))) {
                blocked = "Conflicting animal output " + output.get("path"); throw new IOException(blocked);
            }
            if (!Objects.equals(actual, output.get("after"))) AnimalFiles.write(destination, (String) output.get("after"));
        }
        if (journal.containsKey("estate") && !guard.test(journal)) { blocked = "Estate guard changed before receipt"; throw new IOException(blocked); }
        journal.put("done", true);
        AnimalFiles.write(journalPath, journal.toJSONString());
    }
    void commit(Map<String, JSONObject> outputs, JSONObject metadata) throws IOException {
        if (blocked != null) throw new IOException(blocked);
        JSONObject journal = AnimalFiles.copy(metadata);
        journal.put("format", 1L);
        journal.putIfAbsent("kind", "gameplay");
        JSONObject canonical = new JSONObject();
        rows.forEach((id, row) -> { if (!held.containsKey(id)) canonical.put(id.toString(), AnimalFiles.copy(row)); });
        journal.put("canonical", canonical);
        boolean unfinishedToken = false;
        if (!"migration".equals(journal.get("kind"))) {
            JSONObject holding = new JSONObject();held.forEach((id, reason) -> holding.put(id.toString(), reason));journal.put("held", holding);
            JSONObject tokens = new JSONObject();
            for (Path receipt : files("ownership/token-effects")) {
                JSONObject effect = AnimalFiles.object(Files.readString(receipt));
                if (!"done".equals(effect.get("status"))) unfinishedToken = true;
                if (held.containsKey(UUID.fromString((String) effect.get("animal"))) && !"done".equals(effect.get("status"))) {
                    if (!receipt.getFileName().toString().equals(effect.get("id") + ".json")) throw new IOException("Token effect ID/path conflict");
                    tokens.put(effect.get("id"), effect);
                }
            }
            journal.put("tokenHolds", tokens);
        }
        if (tokenEffectId != null) journal.put("tokenEffect", tokenEffectId);
        String id = String.format("%013d", System.currentTimeMillis()) + "-" + UUID.randomUUID();
        journal.put("id", id); journal.put("done", false);
        JSONArray writes = new JSONArray();
        for (var entry : outputs.entrySet()) {
            String before = AnimalFiles.read(path(entry.getKey()));
            if (expected.containsKey(entry.getKey()) && !Objects.equals(before, expected.get(entry.getKey()))) throw new IOException("Post-load animal file edit " + entry.getKey());
            JSONObject output = new JSONObject(); output.put("path", entry.getKey()); output.put("before", before); output.put("after", entry.getValue().toJSONString()); writes.add(output);
        }
        journal.put("outputs", writes);
        Path receipt = path("ownership/journals/" + id + ".json");
        if ("gameplay".equals(journal.get("kind")) && tokenEffectId == null && !unfinishedToken
            && writes.stream().allMatch(value -> Objects.equals(((JSONObject) value).get("before"), ((JSONObject) value).get("after")))) {
            verifySources();
            Set<String> inventory = new HashSet<>();
            for (Path current : files("CharacterData")) inventory.add(relative(current));
            if (Files.exists(path("character-unowned.json"))) inventory.add("character-unowned.json");
            if (!inventory.equals(expected.keySet())) throw new IOException("Post-load animal file inventory changed");
            for (Path pending : files("ownership/journals"))
                if (!Boolean.TRUE.equals(AnimalFiles.object(Files.readString(pending)).get("done"))) throw new IOException("Unfinished animal journal " + pending.getFileName());
            validateReceipt(receipt, journal);
            return;
        }
        AnimalFiles.write(receipt, journal.toJSONString());
        ready = false;
        publish(receipt, journal);
        lastJournalId = id;
        outputs.forEach((name, document) -> expected.put(name, document.toJSONString()));
        ready = true;
    }
    void migrate(Function<UUID, Optional<CharacterRef>> heirs) throws IOException {
        if (!files("CharacterData").isEmpty() || Files.exists(path("character-unowned.json"))) throw new IOException("Unjournaled character files require review");
        Map<UUID, Optional<CharacterRef>> fixed = new HashMap<>();
        Path heirPlan = path("ownership/legacy-heirs.json");
        if (Files.exists(heirPlan)) {
            JSONObject prior = AnimalFiles.object(Files.readString(heirPlan));
            prior.forEach((account, ref) -> fixed.put(UUID.fromString((String) account), Optional.ofNullable(ref).map(value -> parseRef((JSONObject) value))));
            heirs = account -> fixed.getOrDefault(account, Optional.empty());
        }
        Function<UUID, Optional<CharacterRef>> lookup = heirs;
        Function<UUID, Optional<CharacterRef>> reviewed = account -> fixed.computeIfAbsent(account, lookup);
        List<Path> sources = new ArrayList<>(files("PlayerData"));
        Path unowned = path("unownedanimals.json"); if (Files.exists(unowned)) sources.add(unowned);
        JSONObject backup = new JSONObject(), inventory = new JSONObject();
        Map<UUID, List<JSONObject>> copies = new LinkedHashMap<>();
        for (Path source : sources) {
            String name = relative(source), text = Files.readString(source); backup.put(name, text);
            JSONObject document = AnimalFiles.object(text);
            if (document.containsKey("owner") || document.containsKey("format")) throw new IOException("Reserved character envelope fields in legacy source " + name);
            JSONArray animals = (JSONArray) document.get("animals");
            if (animals == null) throw new IOException("Missing animal inventory " + name);
            UUID filename = source.equals(unowned) ? null : UUID.fromString(source.getFileName().toString().replace(".json", ""));
            if (filename == null) {
                JSONObject destination = AnimalFiles.copy(document);destination.put("format",1L);destination.put("animals",new JSONArray());documents.put("character-unowned.json",destination);
            }
            if (filename != null) {
                var heir = reviewed.apply(filename).filter(ref -> ref.ownerId().equals(filename));
                if (heir.isPresent()) {
                    JSONObject destination = AnimalFiles.copy(document); destination.put("format", 1L); destination.put("owner", ref(heir.get())); destination.put("animals", new JSONArray());
                    documents.put("CharacterData/" + heir.get().characterId() + ".json", destination);
                }
            }
            for (Object value : animals) {
                JSONObject row = AnimalFiles.copy((JSONObject) value);
                if (row.containsKey("characterOwners") || row.containsKey("heldOwners")) throw new IOException("Reserved character fields in legacy animal " + row.get("uuid"));
                UUID animal = UUID.fromString((String) row.get("uuid"));
                Set<String> owners = new TreeSet<>();
                for (Object owner : (JSONArray) row.get("ownersUuids")) owners.add(UUID.fromString((String) owner).toString());
                if (filename != null) owners.add(filename.toString());
                JSONArray effective = new JSONArray(); effective.addAll(owners); row.put("ownersUuids", effective);
                copies.computeIfAbsent(animal, ignored -> new ArrayList<>()).add(row);
            }
        }
        Path archived = path("ownership/source.json");
        if (Files.exists(archived) && !AnimalFiles.object(Files.readString(archived)).equals(backup)) throw new IOException("Legacy source changed after archive");
        if (!Files.exists(archived)) AnimalFiles.write(archived, backup.toJSONString());
        JSONObject canonical = new JSONObject(), heldPlan = new JSONObject();
        for (var entry : copies.entrySet()) {
            JSONObject row = entry.getValue().getFirst();
            if (entry.getValue().stream().anyMatch(copy -> !copy.equals(row))) { heldPlan.put(entry.getKey().toString(), "Conflicting legacy copies"); continue; }
            JSONObject migrated = AnimalFiles.copy(row); JSONArray owners = new JSONArray(), heldOwners = new JSONArray();
            for (Object value : (JSONArray) row.get("ownersUuids")) {
                UUID account = UUID.fromString((String) value); Optional<CharacterRef> heir = reviewed.apply(account);
                if (heir.isPresent() && account.equals(heir.get().ownerId())) owners.add(ref(heir.get()));
                else { JSONObject slot = new JSONObject(); slot.put("account", value); slot.put("reason", "No reviewed heir or wrong account"); heldOwners.add(slot); }
            }
            migrated.put("characterOwners", owners); migrated.put("heldOwners", heldOwners);
            rows.put(entry.getKey(), migrated); canonical.put(entry.getKey().toString(), migrated);
        }
        JSONObject mappings = new JSONObject();fixed.forEach((account, heir) -> mappings.put(account.toString(), heir.filter(value -> value.ownerId().equals(account)).map(AnimalOwnershipStore::ref).orElse(null)));
        if (!Files.exists(heirPlan)) AnimalFiles.write(heirPlan, mappings.toJSONString());
        inventory.put("format", 1L); inventory.put("canonical", canonical); inventory.put("held", heldPlan); inventory.put("sources", backup);
        Map<String, JSONObject> outputs = fanout(); outputs.put("ownership/migration.json", inventory);
        JSONObject metadata = new JSONObject(); metadata.put("kind", "migration"); metadata.put("canonical", canonical); metadata.put("held", heldPlan);
        commit(outputs, metadata);
    }
    void load() throws IOException {
        rows.clear(); held.clear(); documents.clear(); expected.clear();
        JSONObject migration = AnimalFiles.object(Files.readString(path("ownership/migration.json")));
        ((JSONObject) migration.get("held")).forEach((id, reason) -> held.put(UUID.fromString((String) id), (String) reason));
        List<Path> sources = new ArrayList<>(files("CharacterData"));
        Path unowned = path("character-unowned.json"); if (Files.exists(unowned)) sources.add(unowned);
        for (Path source : sources) {
            String text = Files.readString(source), name = relative(source); JSONObject document = AnimalFiles.object(text);
            documents.put(name, document); expected.put(name, text);
            CharacterRef filename = source.equals(unowned) ? null : parseRef((JSONObject) document.get("owner"));
            if (filename != null && !source.getFileName().toString().equals(filename.characterId() + ".json")) throw new IOException("Character filename disagrees with ref");
            for (Object value : (JSONArray) document.get("animals")) {
                JSONObject row = (JSONObject) value; UUID animal = UUID.fromString((String) row.get("uuid"));
                JSONObject existing = rows.putIfAbsent(animal, row);
                if ((existing != null && !existing.equals(row)) || (filename != null && !owners(row).contains(filename)) || (filename == null && !owners(row).isEmpty())) held.put(animal, "Conflicting character fanout");
            }
        }
        for (Path receipt : files("ownership/token-effects")) {
            JSONObject effect = AnimalFiles.object(Files.readString(receipt));
            if (!"done".equals(effect.get("status"))) held.put(UUID.fromString((String) effect.get("animal")), "Unfinished token effect " + effect.get("id"));
        }
    }
    Map<String, JSONObject> fanout() throws IOException {
        Map<String, JSONObject> output = new LinkedHashMap<>();
        for (var entry : documents.entrySet()) {
            JSONObject document = AnimalFiles.copy(entry.getValue()); JSONArray preserved = new JSONArray();
            for (Object row : (JSONArray) document.get("animals")) if (held.containsKey(UUID.fromString((String) ((JSONObject) row).get("uuid")))) preserved.add(row);
            document.put("animals", preserved); output.put(entry.getKey(), document);
        }
        output.computeIfAbsent("character-unowned.json", ignored -> document(null));
        for (var entry : rows.entrySet()) {
            if (held.containsKey(entry.getKey())) continue;
            JSONObject row = entry.getValue(); Set<CharacterRef> owners = owners(row);
            if (owners.isEmpty()) ((JSONArray) output.get("character-unowned.json").get("animals")).add(row);
            for (CharacterRef owner : owners) {
                String name = "CharacterData/" + owner.characterId() + ".json";
                JSONObject document = output.computeIfAbsent(name, ignored -> document(owner));
                if (!owner.equals(parseRef((JSONObject) document.get("owner")))) throw new IOException("Conflicting character account " + name);
                ((JSONArray) document.get("animals")).add(row);
            }
        }
        return output;
    }
    void save(Collection<FarmAnimal> animals) throws IOException {
        if (!ready) throw new IOException("Animal ownership unavailable");
        for (FarmAnimal animal : animals) if (!held.containsKey(animal.getUuid())) rows.put(animal.getUuid(), AnimalCodec.patch(rows.get(animal.getUuid()), animal));
        Map<String, JSONObject> outputs = fanout();
        JSONObject metadata = new JSONObject(); metadata.put("kind", "gameplay"); commit(outputs, metadata);
        documents.clear(); documents.putAll(outputs);
    }
    void forget(UUID animal) throws IOException { if (!ready || held.containsKey(animal)) throw new IOException("Held animal"); rows.remove(animal); }
    boolean held(UUID animal) { return !ready || held.containsKey(animal); }
    boolean hasOwner(UUID animal) {
        if (held(animal)) return true;
        JSONObject row = rows.get(animal); return row != null && (!owners(row).isEmpty() || !((JSONArray) row.get("heldOwners")).isEmpty());
    }
    static JSONObject document(CharacterRef ref) { JSONObject doc = new JSONObject(); doc.put("format", 1L); if (ref != null) doc.put("owner", ref(ref)); doc.put("animals", new JSONArray()); return doc; }
    static JSONObject ref(CharacterRef ref) { JSONObject value = new JSONObject(); value.put("account", ref.ownerId().toString()); value.put("character", ref.characterId().toString()); return value; }
    static CharacterRef parseRef(JSONObject value) { return new CharacterRef(UUID.fromString((String) value.get("account")), UUID.fromString((String) value.get("character"))); }
    static Set<CharacterRef> owners(JSONObject row) { Set<CharacterRef> refs = new LinkedHashSet<>(); for (Object value : (JSONArray) row.get("characterOwners")) refs.add(parseRef((JSONObject) value)); return refs; }
}
