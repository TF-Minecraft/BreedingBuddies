package net.tfminecraft.breedingbuddies.husbandry;

public final class HusbandryDropEntry {

    private final String path;
    private final int amount;
    private final int weight;

    public HusbandryDropEntry(String path, int amount, int weight) {
        this.path = path == null ? "" : path;
        this.amount = Math.max(1, amount);
        this.weight = Math.max(1, weight);
    }

    public String path() {
        return path;
    }

    public int amount() {
        return amount;
    }

    public int weight() {
        return weight;
    }
}
