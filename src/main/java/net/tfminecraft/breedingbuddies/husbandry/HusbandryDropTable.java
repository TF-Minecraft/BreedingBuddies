package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;

public final class HusbandryDropTable {

    private final List<HusbandryDropEntry> common;
    private final List<HusbandryDropEntry> rare;
    private final List<HusbandryDropEntry> epic;
    private final List<HusbandryDropEntry> legendary;
    private final boolean counted;

    public HusbandryDropTable(
            List<HusbandryDropEntry> common,
            List<HusbandryDropEntry> rare,
            List<HusbandryDropEntry> epic,
            List<HusbandryDropEntry> legendary) {
        this(common, rare, epic, legendary, false);
    }

    public HusbandryDropTable(
            List<HusbandryDropEntry> common,
            List<HusbandryDropEntry> rare,
            List<HusbandryDropEntry> epic,
            List<HusbandryDropEntry> legendary,
            boolean counted) {
        this.common = List.copyOf(common == null ? List.of() : common);
        this.rare = List.copyOf(rare == null ? List.of() : rare);
        this.epic = List.copyOf(epic == null ? List.of() : epic);
        this.legendary = List.copyOf(legendary == null ? List.of() : legendary);
        this.counted = counted;
    }

    public static HusbandryDropTable empty() {
        return new HusbandryDropTable(List.of(), List.of(), List.of(), List.of(), false);
    }

    public boolean counted() {
        return counted;
    }

    public List<HusbandryDropEntry> common() {
        return common;
    }

    public List<HusbandryDropEntry> rare() {
        return rare;
    }

    public List<HusbandryDropEntry> epic() {
        return epic;
    }

    public List<HusbandryDropEntry> legendary() {
        return legendary;
    }

    public boolean isEmpty() {
        return common.isEmpty() && rare.isEmpty() && epic.isEmpty() && legendary.isEmpty();
    }
}
