package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.EntityType;

public final class HusbandrySpecies {

    private final EntityType type;
    private final boolean milk;
    private final String slaughterMeat;
    private final HusbandryDropTable slaughterDrops;
    private final HusbandryDropTable shearDrops;
    private final HusbandryDropTable shedDrops;
    private final String egg;
    private final int growUpSeconds;
    private final int woolTimerSeconds;
    private final int milkTimerSeconds;
    private final HusbandryExpBracket exp;

    public HusbandrySpecies(
            EntityType type,
            boolean milk,
            String slaughterMeat,
            HusbandryDropTable slaughterDrops,
            HusbandryDropTable shearDrops,
            HusbandryDropTable shedDrops,
            String egg,
            int growUpSeconds,
            int woolTimerSeconds,
            int milkTimerSeconds) {
        this(type, milk, slaughterMeat, slaughterDrops, shearDrops, shedDrops, egg,
                growUpSeconds, woolTimerSeconds, milkTimerSeconds, null);
    }

    public HusbandrySpecies(
            EntityType type,
            boolean milk,
            String slaughterMeat,
            HusbandryDropTable slaughterDrops,
            HusbandryDropTable shearDrops,
            HusbandryDropTable shedDrops,
            String egg,
            int growUpSeconds,
            int woolTimerSeconds,
            int milkTimerSeconds,
            HusbandryExpBracket exp) {
        this.type = type;
        this.milk = milk;
        this.slaughterMeat = slaughterMeat == null ? "" : slaughterMeat;
        this.slaughterDrops = slaughterDrops == null ? HusbandryDropTable.empty() : slaughterDrops;
        this.shearDrops = shearDrops == null ? HusbandryDropTable.empty() : shearDrops;
        this.shedDrops = shedDrops == null ? HusbandryDropTable.empty() : shedDrops;
        this.egg = egg == null ? "" : egg;
        this.growUpSeconds = Math.max(0, growUpSeconds);
        this.woolTimerSeconds = Math.max(0, woolTimerSeconds);
        this.milkTimerSeconds = Math.max(0, milkTimerSeconds);
        this.exp = exp;
    }

    public EntityType type() {
        return type;
    }

    public boolean canMilk() {
        return milk;
    }

    public boolean canSlaughter() {
        return !slaughterMeat.isBlank();
    }

    public boolean canShear() {
        return !shearDrops.isEmpty();
    }

    public boolean canShed() {
        return !shedDrops.isEmpty();
    }

    public boolean hasEgg() {
        return !egg.isBlank();
    }

    public String slaughterMeat() {
        return slaughterMeat;
    }

    public HusbandryDropTable slaughterDrops() {
        return slaughterDrops;
    }

    public HusbandryDropTable shearDrops() {
        return shearDrops;
    }

    public HusbandryDropTable shedDrops() {
        return shedDrops;
    }

    public String egg() {
        return egg;
    }

    public int growUpSeconds() {
        return growUpSeconds;
    }

    public int woolTimerSeconds() {
        return woolTimerSeconds;
    }

    public int milkTimerSeconds() {
        return milkTimerSeconds;
    }

    public HusbandryExpBracket exp() {
        return exp;
    }

    public boolean vanillaEggs() {
        return egg.isBlank() || "vanilla".equalsIgnoreCase(egg);
    }
}
