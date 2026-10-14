package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class HusbandryCoOwnTokenTest {

    private static final UUID ANIMAL = UUID.randomUUID();

    @Test
    void anotherPlayerAcceptsATokenLinkedToAnOwnedAnimal() {
        assertTrue(HusbandryCoOwnToken.accepts(ANIMAL, true, false));
    }

    @Test
    void ownersKeepLinkingAndGivingInsteadOfAccepting() {
        assertFalse(HusbandryCoOwnToken.accepts(ANIMAL, true, true));
    }

    @Test
    void tokensForAnimalsWithoutOwnersAreNotAccepted() {
        assertFalse(HusbandryCoOwnToken.accepts(ANIMAL, false, false));
    }

    @Test
    void unlinkedTokensAreNotAccepted() {
        assertFalse(HusbandryCoOwnToken.accepts(null, true, false));
    }

    @Test
    void parsesOnlyValidLinks() {
        assertEquals(ANIMAL, HusbandryCoOwnToken.parse(ANIMAL.toString()));
        assertNull(HusbandryCoOwnToken.parse(null));
        assertNull(HusbandryCoOwnToken.parse(" "));
        assertNull(HusbandryCoOwnToken.parse("not-a-uuid"));
    }
}
