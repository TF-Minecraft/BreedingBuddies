package net.tfminecraft.breedingbuddies.husbandry;

public final class HusbandryAmountBand {

    private final int minGenetics;
    private final int roastCuts;
    private final int wool;
    private final int secondaryExtra;

    public HusbandryAmountBand(int minGenetics, int roastCuts, int wool) {
        this(minGenetics, roastCuts, wool, 0);
    }

    public HusbandryAmountBand(int minGenetics, int roastCuts, int wool, int secondaryExtra) {
        this.minGenetics = minGenetics;
        this.roastCuts = roastCuts;
        this.wool = wool;
        this.secondaryExtra = Math.max(0, Math.min(2, secondaryExtra));
    }

    public int minGenetics() {
        return minGenetics;
    }

    public int roastCuts() {
        return roastCuts;
    }

    public int wool() {
        return wool;
    }

    public int secondaryExtra() {
        return secondaryExtra;
    }
}
