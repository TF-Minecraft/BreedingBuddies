package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Durable evidence before token effects; unfinished effects are staff holds, never inventory replay. */
public final class AnimalTokenIntent {
    private final Path path;
    private final JSONObject evidence;
    private final CharacterSession actor, recipient;
    private final ItemStack before;
    private AnimalTokenIntent(Path path, JSONObject evidence, CharacterSession actor, CharacterSession recipient, ItemStack before) {
        this.path=path;this.evidence=evidence;this.actor=actor;this.recipient=recipient;this.before=before;
    }
    public static AnimalTokenIntent prepare(Player player, Player target, FarmAnimal animal, ItemStack token) {
        CharacterSession actor=AnimalIdentity.session(player).orElseThrow(),recipient=AnimalIdentity.session(target).orElseThrow();
        JSONObject evidence=new JSONObject();String id=UUID.randomUUID().toString();
        evidence.put("id",id);evidence.put("animal",animal.getUuid().toString());evidence.put("actor",AnimalOwnershipStore.ref(actor.ref()));evidence.put("recipient",AnimalOwnershipStore.ref(recipient.ref()));
        evidence.put("actorSession",actor.sessionId());evidence.put("recipientSession",recipient.sessionId());
        evidence.put("itemBefore",Base64.getEncoder().encodeToString(token.serializeAsBytes()));evidence.put("amountAfter",token.getAmount()-1L);
        evidence.put("animalBefore",PluginData.ownership().rows.get(animal.getUuid()));evidence.put("status","prepared");
        Path path=PluginData.ownership().root.resolve("ownership/token-effects/"+id+".json");
        AnimalTokenIntent intent=new AnimalTokenIntent(path,evidence,actor,recipient,token.clone());intent.write();PluginData.ownership().tokenEffectId=id;return intent;
    }
    public void finish(Player player, Player target, FarmAnimal animal, ItemStack token, Runnable entityEffect) {
        if (!AnimalIdentity.current(actor,player)||!AnimalIdentity.current(recipient,target)||!before.equals(token)) { hold(animal);throw new IllegalStateException("Token session or payload changed"); }
        if (!PluginData.ownership().ready || !Objects.equals(evidence.get("id"),PluginData.ownership().lastJournalToken)) { hold(animal);throw new IllegalStateException("Token ownership publication is unavailable or unbound"); }
        evidence.put("ownerJournal",PluginData.ownership().lastJournalId);
        evidence.put("animalAfter",AnimalFiles.copy(PluginData.ownership().rows.get(animal.getUuid())));evidence.put("status","debit-prepared");write();
        entityEffect.run();token.setAmount(token.getAmount()-1);
        evidence.put("status","done");write();PluginData.ownership().tokenEffectId=null;
    }
    public void hold(FarmAnimal animal) { PluginData.ownership().tokenEffectId=null;PluginData.ownership().held.put(animal.getUuid(),"Unfinished token effect "+evidence.get("id")); }
    private void write() {
        try { AnimalFiles.write(path,evidence.toJSONString()); }
        catch(IOException failure) { throw new IllegalStateException("Token effect receipt requires review",failure); }
    }
}
