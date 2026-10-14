package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Locale;

public record HusbandryOwned(HusbandryAnimal animal, String role) {

    public HusbandryOwned {
        if (role == null || role.isBlank()) {
            role = "owner";
        } else {
            role = role.toLowerCase(Locale.ROOT);
        }
    }
}
