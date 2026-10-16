package net.tfminecraft.breedingbuddies.husbandry;

public final class HusbandryQualityBand {

    private final int minGenetics;
    private final int stars;

    public HusbandryQualityBand(int minGenetics, int stars) {
        this.minGenetics = minGenetics;
        this.stars = stars;
    }

    public int minGenetics() {
        return minGenetics;
    }

    public int stars() {
        return stars;
    }
}
