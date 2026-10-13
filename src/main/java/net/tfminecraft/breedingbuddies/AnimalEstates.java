package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Optional estate API; never loaded until the capability probe succeeds. */
final class AnimalEstates implements EstateParticipant {
    @Override public String id() { return "breedingbuddies-animals"; }
    static boolean guard(JSONObject journal) {
        try {
            var stored = (JSONObject) journal.get("estate");
            return Estates.decision(UUID.fromString((String) stored.get("death")))
                .filter(d -> d.revision() == ((Number) stored.get("revision")).longValue() && d.toString().equals(stored.get("decision")) && AnimalOwnershipStore.ref(d.testator()).equals(stored.get("from"))
                    && d.property().character().map(AnimalOwnershipStore::ref).equals(Optional.ofNullable(stored.get("to"))))
                .filter(Estates::guardHolds).isPresent();
        } catch (RuntimeException failure) { return false; }
    }
    @Override public Holdings describe(UUID character) {
        AnimalOwnershipStore store = PluginData.ownership();
        if (store == null) return Holdings.NONE;
        List<String> labels = new ArrayList<>();
        for (var entry : store.rows.entrySet()) {
            try { if (AnimalOwnershipStore.owners(entry.getValue()).stream().anyMatch(ref -> ref.characterId().equals(character))) labels.add(entry.getKey().toString() + (store.held(entry.getKey()) ? " (held)" : "")); }
            catch (RuntimeException failure) { labels.add(entry.getKey() + " (invalid ownership)"); }
        }
        return new Holdings(Map.of("animals", labels.size()), labels);
    }
    @Override public Outcome settle(EstateDecision decision) {
        AnimalOwnershipStore store = PluginData.ownership();
        if (store == null) return Outcome.retry("Animal store unavailable");
        if (!Estates.guardHolds(decision)) return Outcome.retry("Estate guard changed");
        try {
            Set<String> moved = new TreeSet<>();
            for (var path : store.files("ownership/journals")) {
                JSONObject journal = AnimalFiles.object(Files.readString(path));
                if (!(journal.get("estate") instanceof JSONObject previous)) continue;
                if (!decision.deathId().toString().equals(previous.get("death"))) continue;
                store.validateReceipt(path, journal);
                if (!AnimalOwnershipStore.ref(decision.testator()).equals(previous.get("from"))) return Outcome.blocked("Estate testator conflicts with receipt");
                if (((Number) previous.get("revision")).longValue() == decision.revision()
                    && (!decision.toString().equals(previous.get("decision"))
                        || !decision.property().character().map(AnimalOwnershipStore::ref).equals(Optional.ofNullable(previous.get("to"))))) return Outcome.blocked("Same-revision animal decision conflicts");
                if (!Boolean.TRUE.equals(journal.get("done"))) {
                    if (!guard(journal)) return Outcome.blocked("Stale or incomplete animal estate fanout");
                    store.publish(path, journal); store.load(); store.ready = true; PluginData.rebuildAnimals();
                }
                for (Object id : (JSONArray) journal.get("animals")) moved.add((String) id);
            }
            if (!store.ready) return Outcome.blocked("Animal store requires migration/recovery");
            for (var entry : store.rows.entrySet()) {
                if (moved.contains(entry.getKey().toString()) && AnimalOwnershipStore.owners(entry.getValue()).contains(decision.testator())) return Outcome.blocked("Completed animal restored to testator " + entry.getKey());
            }
            if (decision.property().kind() != Recipient.Kind.CHARACTER) return done(moved);
            CharacterRef target = decision.property().character().orElseThrow();
            if (target.equals(decision.testator())) return done(moved);
            boolean hasSource = false;
            for (var entry : store.rows.entrySet()) {
                Set<CharacterRef> owners = AnimalOwnershipStore.owners(entry.getValue());
                if (!owners.contains(decision.testator())) continue;
                if (store.held(entry.getKey())) return Outcome.blocked("Held animal " + entry.getKey());
                if (owners.stream().anyMatch(owner -> owner.characterId().equals(target.characterId()) && !owner.equals(target))) return Outcome.blocked("Recipient account conflicts " + entry.getKey());
                hasSource = true;
            }
            if (hasSource) {
                PluginData.saveAnimals();
                if (!Estates.guardHolds(decision)) return Outcome.retry("Estate guard changed after care flush");
            }
            Map<UUID, JSONObject> original = new LinkedHashMap<>(store.rows);
            JSONArray current = new JSONArray(); JSONObject canonical = new JSONObject();
            Map<UUID, JSONObject> planned = new LinkedHashMap<>();
            for (var entry : store.rows.entrySet()) {
                Set<CharacterRef> owners = AnimalOwnershipStore.owners(entry.getValue());
                if (!owners.contains(decision.testator())) continue;
                if (store.held(entry.getKey())) return Outcome.blocked("Held animal " + entry.getKey());
                if (owners.stream().anyMatch(owner -> owner.characterId().equals(target.characterId()) && !owner.equals(target))) return Outcome.blocked("Recipient account conflicts " + entry.getKey());
                FarmAnimal live = OwnershipManager.getAnimal(entry.getKey());
                JSONObject row = live == null ? AnimalFiles.copy(entry.getValue()) : AnimalCodec.patch(entry.getValue(), live);
                row.put("characterOwners", entry.getValue().get("characterOwners"));
                JSONArray slots = new JSONArray();
                for (Object value : (JSONArray) row.get("characterOwners")) if (!AnimalOwnershipStore.parseRef((JSONObject) value).equals(decision.testator())) slots.add(value);
                if (!owners.contains(target)) slots.add(AnimalOwnershipStore.ref(target));
                row.put("characterOwners", slots); planned.put(entry.getKey(), row); current.add(entry.getKey().toString()); canonical.put(entry.getKey().toString(), row);
            }
            if (!current.isEmpty()) {
                JSONObject estate = new JSONObject(); estate.put("death", decision.deathId().toString()); estate.put("revision", decision.revision()); estate.put("decision", decision.toString());
                estate.put("from", AnimalOwnershipStore.ref(decision.testator())); estate.put("to", AnimalOwnershipStore.ref(target));
                JSONObject metadata = new JSONObject(); metadata.put("kind", "estate"); metadata.put("estate", estate); metadata.put("animals", current); metadata.put("canonical", canonical);
                try { store.rows.putAll(planned); store.commit(store.fanout(), metadata); store.load(); PluginData.rebuildAnimals(); }
                catch (IOException failure) { store.rows.clear(); store.rows.putAll(original); throw failure; }
                for (Object id : current) moved.add((String) id);
            }
            return done(moved);
        } catch (Exception failure) {
            store.ready = false;
            return (failure instanceof IOException || failure.getCause() instanceof IOException) && store.blocked() == null ? Outcome.retry("Animal estate I/O: " + failure.getMessage()) : Outcome.blocked(store.blocked() == null ? "Invalid animal estate metadata: " + failure.getMessage() : store.blocked());
        }
    }
    private static Outcome done(Set<String> moved) { return Outcome.done(Map.of("animals", moved.size()), List.copyOf(moved)); }
    static void start(Plugin plugin) {
        AnimalEstates participant = new AnimalEstates();
        Bukkit.getServicesManager().register(EstateParticipant.class, participant, plugin, ServicePriority.Normal);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (EstateDecision decision : Estates.decided()) {
                try { Estates.report(decision.deathId(), participant.id(), decision.revision(), participant.settle(decision)); }
                catch (RuntimeException failure) { plugin.getLogger().warning("Animal estate " + decision.deathId() + " revision " + decision.revision() + ": " + failure.getMessage()); }
            }
        }, 3L, 20L * 60 * 60);
    }
}
