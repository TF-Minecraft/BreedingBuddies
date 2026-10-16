package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.List;

public final class HusbandryProducts {

    private HusbandryProducts() {}

    public static boolean hasProducts(HusbandrySpecies species) {
        if (species == null) {
            return false;
        }
        return species.canSlaughter()
                || species.canShear()
                || species.canShed()
                || species.canMilk()
                || species.hasEgg();
    }

    public static List<String> modeLines(HusbandrySpecies species) {
        List<String> lines = new ArrayList<>();
        if (species == null) {
            return lines;
        }
        if (species.canSlaughter()) {
            lines.add("On slaughter");
        }
        if (species.canShear()) {
            lines.add("Shear");
        }
        if (species.canShed()) {
            lines.add("Shed");
        }
        if (species.canMilk()) {
            lines.add("Milk");
        }
        if (species.hasEgg()) {
            lines.add("Eggs");
        }
        return lines;
    }
}
