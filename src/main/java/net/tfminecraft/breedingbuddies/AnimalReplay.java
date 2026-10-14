package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterRef;
import org.json.simple.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Validate semantics from captured source bytes before trusting any replay output. */
final class AnimalReplay {
    private AnimalReplay() {}
    private static void require(boolean valid, String cause) throws IOException { if (!valid) throw new IOException(cause); }
    static void validate(AnimalOwnershipStore store, Path path, JSONObject journal) throws IOException {
        require(Long.valueOf(1).equals(journal.get("format")),"Unsupported animal journal version");
        String kind=(String)journal.get("kind");require(Set.of("migration","gameplay","estate").contains(kind),"Unsupported journal kind");
        require(path.getFileName().toString().equals(journal.get("id")+".json"),"Journal ID/path conflict");
        boolean estate=kind.equals("estate");
        require(estate==journal.containsKey("estate")&&estate==journal.containsKey("animals"),"Journal kind/envelope conflict");
        JSONObject canonical=(JSONObject)journal.get("canonical"),holding=(JSONObject)journal.get("held");
        Map<String,JSONObject> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();JSONObject migration=null;
        for(Object value:(JSONArray)journal.get("outputs")) {
            JSONObject output=(JSONObject)value;String name=(String)output.get("path");store.path(name);
            require(!after.containsKey(name),"Duplicate output path");
            if(name.equals("ownership/migration.json")) {
                require(kind.equals("migration")&&migration==null&&output.get("before")==null,"Unexpected migration output");
                migration=AnimalFiles.object((String)output.get("after"));continue;
            }
            CharacterRef owner=owner(name,AnimalFiles.object((String)output.get("after")));
            JSONObject desired=AnimalFiles.object((String)output.get("after"));after.put(name,desired);
            if(output.get("before")!=null) {
                JSONObject prior=AnimalFiles.object((String)output.get("before"));require(Objects.equals(owner,owner(name,prior)),"Document owner changed");before.put(name,prior);
                JSONObject first=AnimalFiles.copy(prior),second=AnimalFiles.copy(desired);first.remove("animals");second.remove("animals");require(first.equals(second),"Envelope fields changed");
            } else if(!kind.equals("migration")) {
                JSONObject blank=AnimalOwnershipStore.document(owner);blank.put("animals",desired.get("animals"));require(blank.equals(desired),"Unexpected new envelope fields");
            }
        }
        if(!kind.equals("migration")) validateHolding(store,before,holding,(JSONObject)journal.get("tokenHolds"));
        Map<String,JSONObject> planned=rows(canonical),original=collect(before,holding);
        require(Collections.disjoint(planned.keySet(),holding.keySet()),"Held animal in canonical plan");
        if(kind.equals("estate")) estate(journal,original,planned);
        if(kind.equals("migration")) {
            require(before.isEmpty()&&migration!=null,"Migration overwrites character documents");
            migration(store,migration,canonical,holding,after);
        } else require(migration==null,"Unexpected migration manifest");
        Set<String> expected=new HashSet<>(before.keySet());expected.add("character-unowned.json");
        if(kind.equals("migration")) {
            JSONObject sources=(JSONObject)migration.get("sources"),heirs=AnimalFiles.object(Files.readString(store.path("ownership/legacy-heirs.json")));
            for(Object key:sources.keySet())if(!key.equals("unownedanimals.json")) {
                String account=Path.of((String)key).getFileName().toString().replace(".json","");
                if(heirs.get(account) instanceof JSONObject ref)expected.add("CharacterData/"+AnimalOwnershipStore.parseRef(ref).characterId()+".json");
            }
        }
        for(JSONObject row:planned.values())for(CharacterRef ref:AnimalOwnershipStore.owners(row))expected.add("CharacterData/"+ref.characterId()+".json");
        require(expected.equals(after.keySet()),"Unexpected/missing output roster path");
        for(var entry:after.entrySet()) {
            CharacterRef owner=owner(entry.getKey(),entry.getValue());Map<String,JSONObject> actual=animalRows(entry.getValue());Set<String> wanted=new HashSet<>();
            for(var row:planned.entrySet()) {
                Set<CharacterRef> owners=AnimalOwnershipStore.owners(row.getValue());boolean belongs=owner==null?owners.isEmpty():owners.contains(owner);
                if(belongs){wanted.add(row.getKey());require(row.getValue().equals(actual.get(row.getKey())),"Canonical animal output changed "+row.getKey());}
            }
            JSONObject prior=before.get(entry.getKey());Map<String,JSONObject> priorRows=prior==null?Map.of():animalRows(prior);
            for(Object id:holding.keySet())if(priorRows.containsKey(id)){wanted.add((String)id);require(priorRows.get(id).equals(actual.get(id)),"Held animal copy changed");}
            require(wanted.equals(actual.keySet()),"Extra/missing animal in output roster");
        }
    }
    private static CharacterRef owner(String path, JSONObject document) throws IOException {
        require(Long.valueOf(1).equals(document.get("format")),"Unsupported character document version");
        require(document.get("animals") instanceof JSONArray,"Missing animal array");
        if(path.equals("character-unowned.json")){require(!document.containsKey("owner"),"Unowned document has an owner");return null;}
        CharacterRef ref=AnimalOwnershipStore.parseRef((JSONObject)document.get("owner"));
        require(path.equals("CharacterData/"+ref.characterId()+".json"),"Character output path/owner mismatch");return ref;
    }
    private static Map<String,JSONObject> rows(JSONObject canonical) throws IOException {
        Map<String,JSONObject> rows=new LinkedHashMap<>();
        for(Object key:canonical.keySet()) {
            UUID id=UUID.fromString((String)key);JSONObject row=(JSONObject)canonical.get(key);require(id.toString().equals(row.get("uuid")),"Canonical UUID changed");
            Set<UUID> characters=new HashSet<>();for(Object value:(JSONArray)row.get("characterOwners")){CharacterRef ref=AnimalOwnershipStore.parseRef((JSONObject)value);require(characters.add(ref.characterId()),"Duplicate character slot");}
            require(row.get("heldOwners") instanceof JSONArray,"Missing held slots");rows.put((String)key,row);
        }
        return rows;
    }
    private static Map<String,JSONObject> animalRows(JSONObject document) throws IOException {
        Map<String,JSONObject> rows=new LinkedHashMap<>();for(Object value:(JSONArray)document.get("animals")){JSONObject row=(JSONObject)value;String id=(String)row.get("uuid");UUID.fromString(id);require(rows.put(id,row)==null,"Duplicate animal in document");}return rows;
    }
    private static void validateHolding(AnimalOwnershipStore store, Map<String,JSONObject> documents, JSONObject holding, JSONObject tokens) throws IOException {
        Set<String> evidence=new HashSet<>(),required=new HashSet<>();
        JSONObject migration=AnimalFiles.object(Files.readString(store.path("ownership/migration.json")));
        evidence.addAll(((JSONObject)migration.get("held")).keySet());required.addAll(evidence);
        Map<String,JSONObject> first=new HashMap<>();
        for(var entry:documents.entrySet()) {
            CharacterRef owner=owner(entry.getKey(),entry.getValue());
            for(var row:animalRows(entry.getValue()).entrySet()) {
                JSONObject previous=first.putIfAbsent(row.getKey(),row.getValue());
                boolean invalid=previous!=null&&!previous.equals(row.getValue());
                try {
                    Set<CharacterRef> owners=AnimalOwnershipStore.owners(row.getValue());
                    invalid|=owner==null?!owners.isEmpty():!owners.contains(owner);
                    AnimalCodec.load(row.getValue());
                } catch(RuntimeException failure) { invalid=true; }
                if(invalid){evidence.add(row.getKey());required.add(row.getKey());}
            }
        }
        for(Object key:tokens.keySet()) {
            UUID id=UUID.fromString((String)key);JSONObject snapshot=(JSONObject)tokens.get(key);
            require(id.toString().equals(snapshot.get("id"))&&Set.of("prepared","debit-prepared").contains(snapshot.get("status")),"Invalid captured token hold identity/status");
            UUID animal=UUID.fromString((String)snapshot.get("animal"));
            AnimalOwnershipStore.parseRef((JSONObject)snapshot.get("actor"));AnimalOwnershipStore.parseRef((JSONObject)snapshot.get("recipient"));
            require(snapshot.get("actorSession") instanceof Long&&snapshot.get("recipientSession") instanceof Long
                &&snapshot.get("amountAfter") instanceof Long&&((Long)snapshot.get("amountAfter"))>=0
                &&Base64.getDecoder().decode((String)snapshot.get("itemBefore")).length>0,"Invalid captured token payload/session");
            require(snapshot.get("animalBefore")==null||animal.toString().equals(((JSONObject)snapshot.get("animalBefore")).get("uuid")),"Captured token animal conflicts");
            JSONObject current=AnimalFiles.object(Files.readString(store.path("ownership/token-effects/"+id+".json")));
            for(String field:List.of("id","animal","actor","recipient","actorSession","recipientSession","itemBefore","amountAfter","animalBefore"))
                require(snapshot.containsKey(field)&&Objects.equals(snapshot.get(field),current.get(field)),"Captured token fixed evidence changed "+field);
            if("debit-prepared".equals(snapshot.get("status")))require(snapshot.get("ownerJournal") instanceof String
                &&snapshot.get("animalAfter") instanceof JSONObject&&animal.toString().equals(((JSONObject)snapshot.get("animalAfter")).get("uuid")),"Captured armed token lacks owner publication");
            for(String field:List.of("ownerJournal","animalAfter"))if(snapshot.containsKey(field))require(Objects.equals(snapshot.get(field),current.get(field)),"Captured token publication evidence changed "+field);
            evidence.add(animal.toString());
        }
        require(evidence.containsAll(holding.keySet())&&holding.keySet().containsAll(required),"Held set lacks captured preservation evidence");
    }
    private static Map<String,JSONObject> collect(Map<String,JSONObject> documents, JSONObject holding) throws IOException {
        Map<String,JSONObject> rows=new LinkedHashMap<>();
        for(var entry:documents.entrySet()) {
            CharacterRef owner=owner(entry.getKey(),entry.getValue());
            for(var row:animalRows(entry.getValue()).entrySet()) {
                if(holding.containsKey(row.getKey()))continue;
                JSONObject previous=rows.putIfAbsent(row.getKey(),row.getValue());require(previous==null||previous.equals(row.getValue()),"Conflicting captured source copies");
                Set<CharacterRef> owners=AnimalOwnershipStore.owners(row.getValue());require(owner==null?owners.isEmpty():owners.contains(owner),"Captured source roster disagrees with slots");
            }
        }
        return rows;
    }
    private static void estate(JSONObject journal, Map<String,JSONObject> before, Map<String,JSONObject> after) throws IOException {
        JSONObject header=(JSONObject)journal.get("estate");CharacterRef from=AnimalOwnershipStore.parseRef((JSONObject)header.get("from")),to=AnimalOwnershipStore.parseRef((JSONObject)header.get("to"));
        require(!from.equals(to)&&header.get("decision") instanceof String&&header.get("revision") instanceof Long,"Invalid estate decision");UUID death=UUID.fromString((String)header.get("death"));
        String decision=(String)header.get("decision");
        require(decision.startsWith("EstateDecision[deathId="+death+", testator="+from+", ")
            &&decision.contains(", property=Character[ref="+to+"], ")
            &&decision.endsWith(", revision="+header.get("revision")+"]"),"Estate header conflicts with full captured decision");
        Set<String> moved=new HashSet<>();for(Object id:(JSONArray)journal.get("animals"))require(moved.add((String)id),"Duplicate moved animal");
        require(before.keySet().equals(after.keySet())&&before.keySet().containsAll(moved),"Estate changed animal inventory");
        Set<String> source=new HashSet<>();before.forEach((id,row)->{if(AnimalOwnershipStore.owners(row).contains(from))source.add(id);});
        require(source.equals(moved),"Estate omits or adds testator animals");
        for(var entry:before.entrySet()) {
            JSONObject expected=AnimalFiles.copy(entry.getValue());
            if(moved.contains(entry.getKey())) {
                Set<CharacterRef> owners=AnimalOwnershipStore.owners(expected);require(owners.contains(from),"Estate source lacks testator slot");
                require(owners.stream().noneMatch(owner->owner.characterId().equals(to.characterId())&&!owner.equals(to)),"Estate recipient account conflict");
                JSONArray slots=new JSONArray();for(Object value:(JSONArray)expected.get("characterOwners"))if(!AnimalOwnershipStore.parseRef((JSONObject)value).equals(from))slots.add(value);
                if(!owners.contains(to))slots.add(AnimalOwnershipStore.ref(to));expected.put("characterOwners",slots);
            }
            require(expected.equals(after.get(entry.getKey())),"Estate changed unrelated ownership/physiology/unknown state "+entry.getKey());
        }
    }
    private static void migration(AnimalOwnershipStore store, JSONObject manifest, JSONObject canonical, JSONObject holding, Map<String,JSONObject> output) throws IOException {
        JSONObject source=AnimalFiles.object(Files.readString(store.path("ownership/source.json"))),heirs=AnimalFiles.object(Files.readString(store.path("ownership/legacy-heirs.json")));
        require(source.equals(manifest.get("sources"))&&canonical.equals(manifest.get("canonical"))&&holding.equals(manifest.get("held")),"Migration manifest conflicts with plan");
        Map<String,List<JSONObject>> copies=new LinkedHashMap<>();
        for(Object key:source.keySet()) {
            String path=(String)key;JSONObject document=AnimalFiles.object((String)source.get(key));UUID filename=path.equals("unownedanimals.json")?null:UUID.fromString(Path.of(path).getFileName().toString().replace(".json",""));
            for(Object value:(JSONArray)document.get("animals")) {
                JSONObject row=AnimalFiles.copy((JSONObject)value);String id=(String)row.get("uuid");Set<String> accounts=new TreeSet<>();for(Object account:(JSONArray)row.get("ownersUuids"))accounts.add(UUID.fromString((String)account).toString());if(filename!=null)accounts.add(filename.toString());JSONArray owners=new JSONArray();owners.addAll(accounts);row.put("ownersUuids",owners);copies.computeIfAbsent(id,ignored->new ArrayList<>()).add(row);
            }
            if(filename==null) {
                JSONObject expected=AnimalFiles.copy(document),actual=AnimalFiles.copy(output.get("character-unowned.json"));expected.put("format",1L);expected.remove("animals");actual.remove("animals");require(expected.equals(actual),"Unowned legacy envelope changed");
            }
            if(filename!=null&&heirs.get(filename.toString()) instanceof JSONObject mapped) {
                CharacterRef ref=AnimalOwnershipStore.parseRef(mapped);JSONObject expected=AnimalFiles.copy(document);expected.put("format",1L);expected.put("owner",mapped);JSONObject actual=AnimalFiles.copy(output.get("CharacterData/"+ref.characterId()+".json"));expected.remove("animals");actual.remove("animals");require(expected.equals(actual),"Legacy envelope changed");
            }
        }
        Set<String> agreed=new HashSet<>(),conflicts=new HashSet<>();
        for(var entry:copies.entrySet()) {
            JSONObject row=entry.getValue().getFirst();if(entry.getValue().stream().anyMatch(copy->!copy.equals(row))){conflicts.add(entry.getKey());continue;}
            agreed.add(entry.getKey());JSONObject expected=AnimalFiles.copy(row);JSONArray owners=new JSONArray(),held=new JSONArray();
            for(Object account:(JSONArray)row.get("ownersUuids")) {
                if(heirs.get(account) instanceof JSONObject ref){require(account.equals(ref.get("account")),"Wrong reviewed heir account");owners.add(ref);}
                else {JSONObject slot=new JSONObject();slot.put("account",account);slot.put("reason","No reviewed heir or wrong account");held.add(slot);}
            }
            expected.put("characterOwners",owners);expected.put("heldOwners",held);require(expected.equals(canonical.get(entry.getKey())),"Migration changed source state or reviewed slots");
        }
        require(agreed.equals(canonical.keySet())&&conflicts.equals(holding.keySet()),"Migration omits/adds source animals");
    }
}
