package net.tfminecraft.breedingbuddies;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import net.tfminecraft.tlibs.character.CharacterRef;
import java.util.*;

final class AnimalCodec {
    private AnimalCodec() {}
    static JSONObject patch(JSONObject raw, FarmAnimal animal) {
        JSONObject row = raw == null ? new JSONObject() : AnimalFiles.copy(raw);
        row.put("uuid", animal.getUuid().toString()); row.put("name", animal.getName());
        JSONArray slots = new JSONArray();
        for (CharacterRef owner : animal.getOwnerRefs().values()) {
            JSONObject slot = AnimalOwnershipStore.ref(owner);
            if (raw != null) for (Object existing : (JSONArray) raw.get("characterOwners")) if (owner.characterId().toString().equals(((JSONObject) existing).get("character"))) slot = AnimalFiles.copy((JSONObject) existing);
            slots.add(slot);
        }
        row.put("characterOwners", slots);
        if (!row.containsKey("heldOwners")) row.put("heldOwners", new JSONArray());
        row.put("stableNeeded", animal.isStableNeeded()); row.put("friendshipPoints", (long) animal.getFriendshipPoints()); row.put("geneticPoints", (long) animal.getGeneticPoints());
        row.put("state", animal.getState().toString()); row.put("fed", animal.getFed()); row.put("cared", animal.getCared()); row.put("sleptInStable", animal.isSleptInStable());
        row.put("spaceNeeded", animal.isSpaceNeeded()); row.put("waterNeeded", animal.isWaterNeeded()); row.put("daysOut", (long) animal.getDaysOut());
        row.put("lastRewardCollected", animal.getLastRewardCollected() == null ? null : animal.getLastRewardCollected().getTime());
        row.put("lastUpdated", animal.getLastUpdated() == null ? null : animal.getLastUpdated().getTime()); return row;
    }
    static FarmAnimal load(JSONObject row) {
        FarmAnimal animal = new FarmAnimal(UUID.fromString((String) row.get("uuid")), (String) row.get("name"), new HashSet<>());
        for (CharacterRef owner : AnimalOwnershipStore.owners(row)) { animal.getOwnerRefs().put(owner.characterId(), owner); animal.getOwnersUuids().add(owner.characterId()); }
        animal.setStableNeeded((boolean) row.get("stableNeeded")); animal.setFriendshipPoints(((Number) row.get("friendshipPoints")).intValue());
        animal.setGeneticPoints(((Number) row.get("geneticPoints")).intValue()); animal.setState(AnimalStates.valueOf((String) row.get("state")));
        animal.setFed((boolean) row.get("fed")); animal.setCared((boolean) row.get("cared")); animal.setSleptInStable((boolean) row.get("sleptInStable"));
        animal.setSpaceNeeded((boolean) row.get("spaceNeeded")); animal.setWaterNeeded((boolean) row.get("waterNeeded")); animal.setDaysOut(((Number) row.get("daysOut")).intValue());
        if (row.get("lastRewardCollected") != null) animal.setLastRewardCollected(new Date(((Number) row.get("lastRewardCollected")).longValue()));
        if (row.get("lastUpdated") != null) animal.setLastUpdated(new Date(((Number) row.get("lastUpdated")).longValue())); return animal;
    }
}
