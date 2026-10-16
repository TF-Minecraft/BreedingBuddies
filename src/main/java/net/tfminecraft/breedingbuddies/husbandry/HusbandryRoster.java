package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class HusbandryRoster {

    private HusbandryRoster() {}

    public static List<Component> render(
            String ownerName,
            boolean self,
            List<HusbandryOwned> owned,
            int maxAnimals,
            long nowMillis) {
        return render(ownerName, self, owned, maxAnimals, nowMillis, uuid -> false);
    }

    public static List<Component> render(
            String ownerName,
            boolean self,
            List<HusbandryOwned> owned,
            int maxAnimals,
            long nowMillis,
            Predicate<UUID> missing) {
        List<HusbandryOwned> rows = owned == null ? List.of() : owned;
        int shown = rows.size();
        int cap = Math.max(1, maxAnimals);
        String shownName = plain(ownerName);
        String title = self || shownName.isEmpty() ? "Your animals" : shownName + "'s animals";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(title + " ", NamedTextColor.GOLD)
                .append(Component.text("(" + shown + "/" + cap + ")", NamedTextColor.GRAY)));
        if (rows.isEmpty()) {
            if (self) {
                lines.add(Component.text("You do not own any animals yet.", NamedTextColor.GRAY));
                lines.add(Component.text("Claim one with a named Ownership Token.", NamedTextColor.GRAY));
            } else {
                String who = shownName.isEmpty() ? "That player" : shownName;
                lines.add(Component.text(who + " does not own any animals.", NamedTextColor.GRAY));
            }
            return lines;
        }
        List<HusbandryOwned> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparing((HusbandryOwned row) -> sortLabel(row).toLowerCase(Locale.ROOT))
                .thenComparing(row -> speciesLabel(row.animal().type()).toLowerCase(Locale.ROOT))
                .thenComparing(row -> row.animal().uuid() == null ? "" : row.animal().uuid().toString()));
        for (HusbandryOwned row : sorted) {
            lines.add(line(row, nowMillis, missing.test(row.animal().uuid())));
        }
        return lines;
    }

    static String speciesLabel(String type) {
        if (type == null || type.isBlank()) {
            return "Animal";
        }
        String raw = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        return out.isEmpty() ? "Animal" : out.toString();
    }

    private static String sortLabel(HusbandryOwned owned) {
        String name = plain(owned.animal().name());
        if (!name.isEmpty()) {
            return name;
        }
        return speciesLabel(owned.animal().type());
    }

    private static Component line(HusbandryOwned owned, long nowMillis, boolean missing) {
        HusbandryAnimal animal = owned.animal();
        String species = speciesLabel(animal.type());
        String name = plain(animal.name());
        boolean named = !name.isEmpty() && !name.equalsIgnoreCase(species);
        Component row = Component.text("• ", NamedTextColor.GOLD)
                .append(Component.text(named ? name : species, NamedTextColor.WHITE));
        if (named) {
            row = row.append(sep()).append(Component.text(species, NamedTextColor.GRAY));
        }
        if ("coowner".equals(owned.role())) {
            row = row.append(sep()).append(Component.text("Co-owner", NamedTextColor.GRAY));
        }
        if (!HusbandryGrowth.isMature(animal, nowMillis)) {
            row = row.append(sep()).append(Component.text("Growing", NamedTextColor.GRAY));
        }
        row = row.append(sep()).append(status(animal));
        if (missing) {
            row = row.append(sep()).append(Component.text("Missing", NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "Not found in any saved chunk when the server started."))));
            if (!animal.hasLocation()) {
                return row;
            }
            return row.append(sep()).append(Component.text("last seen ", NamedTextColor.GRAY)).append(place(animal));
        }
        return row.append(sep()).append(place(animal));
    }

    private static Component status(HusbandryAnimal animal) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (hungry && dirty) {
            return Component.text("Hungry, Dirty", NamedTextColor.RED);
        }
        if (hungry) {
            return Component.text("Hungry", NamedTextColor.GOLD);
        }
        if (dirty) {
            return Component.text("Dirty", NamedTextColor.YELLOW);
        }
        return Component.text("Happy", NamedTextColor.GREEN);
    }

    private static Component place(HusbandryAnimal animal) {
        if (!animal.hasLocation()) {
            return Component.text("location not recorded yet", NamedTextColor.GRAY)
                    .decorate(TextDecoration.ITALIC);
        }
        String coords = animal.x() + ", " + animal.y() + ", " + animal.z();
        String copy = animal.world() + " " + coords;
        return Component.text(animal.world(), NamedTextColor.GRAY)
                .append(Component.space())
                .append(Component.text(coords, NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.copyToClipboard(copy))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy coordinates"))));
    }

    private static Component sep() {
        return Component.text(" · ", NamedTextColor.DARK_GRAY);
    }

    static String plain(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("(?i)§[0-9a-fk-or]", "").replace('\n', ' ').replace('\r', ' ').trim();
    }
}
