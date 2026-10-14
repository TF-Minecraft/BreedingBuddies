package net.tfminecraft.breedingbuddies.husbandry;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class HusbandryProfessionXp {
    private HusbandryProfessionXp() {}

    public static void tryGive(Player player, EntityType type) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            return;
        }
        String profession = HusbandryConfig.professionId();
        if (profession == null || profession.isBlank()) {
            return;
        }
        int amount = HusbandryConfig.expFor(type).roll();
        if (amount <= 0) {
            return;
        }
        try {
            Class<?> grant = Class.forName(
                    "net.tfminecraft.breedingbuddies.husbandry.HusbandryProfessionGrant",
                    true,
                    HusbandryProfessionXp.class.getClassLoader());
            grant.getMethod("give", Player.class, String.class, int.class)
                    .invoke(null, player, profession, amount);
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalAccessException
                | InvocationTargetException | NoSuchMethodException ignored) {
            // MMOCore is present but the profession grant could not run.
        }
    }
}
