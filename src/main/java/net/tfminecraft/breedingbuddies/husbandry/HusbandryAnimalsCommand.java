package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tfminecraft.breedingbuddies.BreedingBuddies;

public final class HusbandryAnimalsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        HusbandryRepository repository = BreedingBuddies.plugin == null ? null : BreedingBuddies.plugin.getHusbandryRepository();
        if (repository == null) {
            sender.sendMessage(Component.text("Animal records are not available right now.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && !sender.hasPermission("breedingbuddies.admin")) {
            sender.sendMessage(Component.text("You can only list your own animals.", NamedTextColor.RED));
            return true;
        }
        boolean self = args.length == 0;
        UUID targetId;
        net.tfminecraft.tlibs.character.CharacterRef targetCharacter = null;
        String ownerName;
        if (self) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED));
                return true;
            }
            targetId = player.getUniqueId();
            targetCharacter = HusbandryIdentity.session(player).map(session -> session.ref()).orElse(null);
            if (targetCharacter == null) {
                sender.sendMessage(Component.text("Choose a character before listing animals.", NamedTextColor.RED));
                return true;
            }
            ownerName = HusbandryIdentity.name(targetCharacter);
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (offline == null) {
                sender.sendMessage(Component.text("No player by that name has joined.", NamedTextColor.RED));
                return true;
            }
            targetId = offline.getUniqueId();
            ownerName = offline.getName() == null ? args[0] : offline.getName();
        }
        List<HusbandryOwned> rows = new ArrayList<>();
        for (HusbandryOwned owned : self ? repository.listForCharacter(targetCharacter) : repository.listForPlayer(targetId)) {
            HusbandryAnimal animal = HusbandryEntities.getLoaded(owned.animal().uuid()).orElse(owned.animal());
            Entity live = Bukkit.getEntity(animal.uuid());
            if (live != null) {
                HusbandryLocation.remember(animal, live);
            }
            rows.add(new HusbandryOwned(animal, owned.role()));
        }
        List<Component> lines = HusbandryRoster.render(ownerName, self, rows, HusbandryConfig.maxAnimals(),
                System.currentTimeMillis(), HusbandryLocator::isMissing);
        if (self) {
            lines.set(0, Component.text("Your character's animals (" + rows.size() + ")", NamedTextColor.GOLD));
            lines.add(1, Component.text("Account animal places: " + repository.countForPlayer(targetId)
                    + "/" + HusbandryConfig.maxAnimals(), NamedTextColor.GRAY));
        }
        for (Component line : lines) {
            sender.sendMessage(line);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("breedingbuddies.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
