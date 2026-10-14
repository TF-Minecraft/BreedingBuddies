package net.tfminecraft.breedingbuddies.husbandry;

import java.util.UUID;

/** Decides when a right-click with a linked Co-Ownership Token accepts it. */
final class HusbandryCoOwnToken {

    private HusbandryCoOwnToken() {}

    /**
     * A token linked to someone else's animal is accepted by any right-click: the air, a block, an
     * animal or a player. Clients follow an entity click with a use-item packet, so every path must
     * agree or the player sees an error and then a success. Owners keep linking and giving.
     */
    static boolean accepts(UUID linkedAnimal, boolean linkedHasOwners, boolean clickerOwnsLinked) {
        return linkedAnimal != null && linkedHasOwners && !clickerOwnsLinked;
    }

    static UUID parse(String linked) {
        if (linked == null || linked.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(linked);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
