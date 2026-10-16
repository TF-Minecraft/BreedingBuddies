package net.tfminecraft.breedingbuddies.husbandry;

import java.util.UUID;

public final class HusbandryOwner {

    private final UUID animalUuid;
    private final UUID playerUuid;
    private final UUID characterUuid;
    private final String role;

    /** Legacy metadata remains representable, but cannot grant or mutate character ownership. */
    @Deprecated
    public HusbandryOwner(UUID animalUuid, UUID playerUuid, String role) {
        this(animalUuid, playerUuid, null, role);
    }

    public HusbandryOwner(UUID animalUuid, UUID playerUuid, UUID characterUuid, String role) {
        this.animalUuid = animalUuid;
        this.playerUuid = playerUuid;
        this.characterUuid = characterUuid;
        this.role = role;
    }

    public UUID animalUuid() {
        return animalUuid;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public UUID characterUuid() { return characterUuid; }

    public net.tfminecraft.tlibs.character.CharacterRef ref() {
        return new net.tfminecraft.tlibs.character.CharacterRef(playerUuid, characterUuid);
    }

    public String role() {
        return role;
    }
}
