package net.tfminecraft.breedingbuddies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.tfminecraft.breedingbuddies.husbandry.HusbandryAnimal;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryConfig;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryEntities;
import net.tfminecraft.breedingbuddies.husbandry.HusbandryRepository;
import net.tfminecraft.breedingbuddies.husbandry.HusbandrySpawner;
import net.tfminecraft.breedingbuddies.husbandry.HusbandrySpecies;

/** Staff commands: reload, spawn and save. */
public final class BreedingBuddiesCommand implements CommandExecutor, TabCompleter {
    static final String PERMISSION = "breedingbuddies.admin";
    private static final String SPAWN_USAGE = "§cUsage: /breedingbuddies spawn <type> [genetics] [care]";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (BreedingBuddies.plugin == null) {
                    sender.sendMessage("§cBreedingBuddies is not running.");
                    return true;
                }
                BreedingBuddies.plugin.reloadAll();
                sender.sendMessage("§aBreedingBuddies config reloaded.");
            }
            case "save" -> save(sender);
            case "spawn" -> spawn(sender, args);
            default -> {
                sender.sendMessage("§e/breedingbuddies reload");
                sender.sendMessage("§e/breedingbuddies spawn <type> [genetics] [care]");
                sender.sendMessage("§e/breedingbuddies save");
            }
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can spawn animals.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(SPAWN_USAGE);
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cUnknown entity type.");
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species == null) {
            sender.sendMessage("§cThat type is not a husbandry species.");
            return;
        }
        int genetics = HusbandryConfig.initialGeneticMax();
        int care = 0;
        if (args.length >= 3) {
            try {
                genetics = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cGenetics must be a number.");
                return;
            }
        }
        if (args.length >= 4) {
            try {
                care = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cCare must be a number.");
                return;
            }
        }
        LivingEntity spawned = HusbandrySpawner.spawn(player, type, genetics, care);
        if (spawned == null) {
            sender.sendMessage("§cFailed to spawn animal.");
            return;
        }
        sender.sendMessage("§aSpawned " + spawned.getName()
                + " genetics " + Math.max(0, Math.min(HusbandryConfig.maxGenetics(), genetics))
                + " care " + Math.max(0, Math.min(HusbandryConfig.careMax(), care)) + ".");
    }

    private void save(CommandSender sender) {
        HusbandryRepository repository = BreedingBuddies.plugin == null ? null : BreedingBuddies.plugin.getHusbandryRepository();
        if (repository == null) {
            sender.sendMessage("§cThe animal database is not open.");
            return;
        }
        List<HusbandryAnimal> loaded = HusbandryEntities.snapshotLoaded();
        if (!loaded.isEmpty()) {
            repository.upsertAnimals(loaded);
        }
        repository.checkpointWal(false);
        sender.sendMessage("§aSaved " + loaded.size() + " loaded animals.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return matching(List.of("reload", "spawn", "save"), args[0].toLowerCase(Locale.ROOT));
        }
        if (!args[0].equalsIgnoreCase("spawn")) {
            return Collections.emptyList();
        }
        return switch (args.length) {
            case 2 -> {
                List<String> types = new ArrayList<>();
                for (EntityType type : HusbandryConfig.species().keySet()) {
                    types.add(type.name());
                }
                types.sort(String.CASE_INSENSITIVE_ORDER);
                yield matching(types, args[1].toUpperCase(Locale.ROOT));
            }
            case 3 -> List.of("0", "20", "1000");
            case 4 -> List.of("0", "100", "200");
            default -> Collections.emptyList();
        };
    }

    private static List<String> matching(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
