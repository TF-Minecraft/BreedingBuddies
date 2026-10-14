package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.Player;

import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.Profession;

public final class HusbandryProfessionGrant {
    private HusbandryProfessionGrant() {}

    public static void give(Player player, String professionId, int amount) {
        if (player == null || professionId == null || professionId.isBlank() || amount <= 0) {
            return;
        }
        Profession profession = MMOCore.plugin.professionManager.get(professionId);
        if (profession == null) {
            return;
        }
        PlayerData.get(player).getCollectionSkills().giveExperience(profession, amount, EXPSource.SOURCE);
    }
}
