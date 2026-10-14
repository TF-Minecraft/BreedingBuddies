package net.tfminecraft.breedingbuddies.husbandry;

public final class HusbandrySlaughterDrops {

    private HusbandrySlaughterDrops() {}

    public static boolean shouldReplaceVanilla(boolean hasSlaughter, boolean mature) {
        return hasSlaughter && mature;
    }

    public static boolean shouldAddRoast(boolean hasSlaughter, boolean mature) {
        return hasSlaughter && mature;
    }
}
