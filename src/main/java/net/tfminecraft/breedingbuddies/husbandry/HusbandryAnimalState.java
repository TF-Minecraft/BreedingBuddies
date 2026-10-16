package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Locale;

public enum HusbandryAnimalState {
    UNTAMED,
    OWNED;

    public static HusbandryAnimalState fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNTAMED;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNTAMED;
        }
    }

    public String storage() {
        return name().toLowerCase(Locale.ROOT);
    }
}
