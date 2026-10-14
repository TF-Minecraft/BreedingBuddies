package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class HusbandryRosterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void emptyRosterTellsTheOwnerHowToClaim() {
        List<String> lines = text(HusbandryRoster.render("Ada", true, List.of(), 15, 1_000L));
        assertEquals(List.of(
                "Your animals (0/15)",
                "You do not own any animals yet.",
                "Claim one with a named Ownership Token."), lines);
    }

    @Test
    void listsNameSpeciesStatusAndCopyableCoordinates() {
        HusbandryAnimal bess = animal("COW", "Bess");
        bess.setLastLocation("world", 120, 64, -340);
        HusbandryAnimal woolly = animal("SHEEP", "§cWoolly");
        woolly.setHungrySince(1L);
        woolly.setDirtySince(2L);
        woolly.setLastLocation("world", 118, 65, -338);
        HusbandryAnimal calf = animal("COW", "");
        calf.setMatureAt(5_000L);
        calf.setDirtySince(1L);

        List<Component> lines = HusbandryRoster.render("Ada", true, List.of(
                new HusbandryOwned(woolly, "owner"),
                new HusbandryOwned(calf, "coowner"),
                new HusbandryOwned(bess, "owner")), 15, 1_000L);

        assertEquals("Your animals (3/15)", PLAIN.serialize(lines.get(0)));
        assertEquals("• Bess · Cow · Happy · world 120, 64, -340", PLAIN.serialize(lines.get(1)));
        assertEquals("• Cow · Co-owner · Growing · Dirty · location not recorded yet", PLAIN.serialize(lines.get(2)));
        assertEquals("• Woolly · Sheep · Hungry, Dirty · world 118, 65, -338", PLAIN.serialize(lines.get(3)));

        ClickEvent copy = findClick(lines.get(1));
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, copy.action());
        assertEquals("world 120, 64, -340", copy.value());
        assertNull(findClick(lines.get(2)));
    }

    @Test
    void missingAnimalsAreFlaggedWithTheirLastKnownSpot() {
        HusbandryAnimal bess = animal("COW", "Bess");
        bess.setLastLocation("TFMC_Map", 4369, 167, 1950);
        HusbandryAnimal ham = animal("PIG", "Ham");
        HusbandryAnimal woolly = animal("SHEEP", "Woolly");
        woolly.setLastLocation("TFMC_Map", 10, 64, 10);

        List<String> lines = text(HusbandryRoster.render("Ada", true, List.of(
                new HusbandryOwned(bess, "owner"),
                new HusbandryOwned(ham, "owner"),
                new HusbandryOwned(woolly, "owner")), 15, 1_000L,
                uuid -> !uuid.equals(woolly.uuid())));

        assertEquals(List.of(
                "Your animals (3/15)",
                "• Bess · Cow · Happy · Missing · last seen TFMC_Map 4369, 167, 1950",
                "• Ham · Pig · Happy · Missing",
                "• Woolly · Sheep · Happy · TFMC_Map 10, 64, 10"), lines);
    }

    @Test
    void otherPlayersEmptyListUsesTheirName() {
        List<String> lines = text(HusbandryRoster.render("Ada", false, List.of(), 15, 1_000L));
        assertEquals(List.of(
                "Ada's animals (0/15)",
                "Ada does not own any animals."), lines);
    }

    private static List<String> text(List<Component> lines) {
        return lines.stream().map(PLAIN::serialize).toList();
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent found = findClick(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static HusbandryAnimal animal(String type, String name) {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), type, name);
        animal.setLastProcessedAt(1L);
        return animal;
    }

    @Test
    void speciesLabelsAreReadable() {
        assertEquals("Cow", HusbandryRoster.speciesLabel("COW"));
        assertEquals("Mushroom Cow", HusbandryRoster.speciesLabel("MUSHROOM_COW"));
        assertEquals("Animal", HusbandryRoster.speciesLabel(" "));
        assertTrue(HusbandryRoster.plain("§aBess\n").equals("Bess"));
    }
}
