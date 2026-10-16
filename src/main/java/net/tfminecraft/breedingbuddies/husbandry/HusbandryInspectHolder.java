package net.tfminecraft.breedingbuddies.husbandry;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HusbandryInspectHolder implements InventoryHolder {

    private final UUID animalUuid;
    private Inventory inventory;
    private final net.tfminecraft.tlibs.character.CharacterSession session;
    private long confirmUntil;

    public HusbandryInspectHolder(UUID animalUuid, net.tfminecraft.tlibs.character.CharacterSession session) {
        this.animalUuid = animalUuid;
        this.session = session;
    }

    public boolean isCurrent(org.bukkit.entity.Player player) { return HusbandryIdentity.current(session, player); }

    public boolean belongsTo(net.tfminecraft.tlibs.character.CharacterSession outgoing) { return outgoing.equals(session); }

    /** Giving an animal up takes a second click within five seconds. */
    boolean confirming(long nowMillis) { return nowMillis < confirmUntil; }

    void askToConfirm(long nowMillis) { confirmUntil = nowMillis + 5_000L; }

    public UUID animalUuid() {
        return animalUuid;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
