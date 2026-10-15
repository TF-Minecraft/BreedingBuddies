package net.tfminecraft.breedingbuddies.husbandry;


final class HusbandryGuiBars {

    static final int BAR_SLOTS = 5;

    private HusbandryGuiBars() {}

    static int yieldPercent(HusbandryAnimal animal) {
        if (animal == null) {
            return 0;
        }
        int effective = HusbandryConfig.effectiveGenetics(animal);
        int max = HusbandryConfig.maxGenetics();
        return (int) Math.round(100.0 * effective / max);
    }

    static int filledSegments(int value, int max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(BAR_SLOTS, (int) Math.round(BAR_SLOTS * (double) value / max)));
    }
}
