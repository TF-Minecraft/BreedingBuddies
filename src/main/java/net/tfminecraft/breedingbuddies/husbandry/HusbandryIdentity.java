package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/** New heir and estate entry points are linked only after a capability probe. */
public final class HusbandryIdentity {
    private HusbandryIdentity() {}

    public static Optional<CharacterSession> session(Player player) {
        return player == null ? Optional.empty() : Characters.currentSession(player).filter(session -> current(session, player));
    }

    public static boolean current(CharacterSession session, Player player) {
        return session != null && player != null && session.ref().ownerId().equals(player.getUniqueId())
                && Characters.isCurrent(session);
    }

    public static String name(CharacterRef ref) {
        return Characters.info(ref.characterId()).filter(info -> info.ref().equals(ref))
                .map(info -> info.name()).orElse(ref.characterId().toString());
    }

    public static boolean supports(ClassLoader loader, boolean estates) {
        try {
            Class<?> characters = Class.forName("net.tfminecraft.tlibs.character.Characters", false, loader);
            characters.getMethod("legacyHeirsReady");
            characters.getMethod("legacyHeir", UUID.class);
            if (estates) {
                for (String name : new String[]{"Estates", "EstateParticipant", "EstateDecision", "Outcome", "Holdings"}) {
                    Class.forName("net.tfminecraft.tlibs.character.estate." + name, false, loader);
                }
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    public static void start(Plugin plugin, HusbandryRepository repository) {
        if (repository == null) return;
        ClassLoader loader = HusbandryIdentity.class.getClassLoader();
        if (!supports(loader, false)) {
            plugin.getLogger().warning("Reviewed animal heirs require TLibs 2.6.0; old owner slots remain held.");
            return;
        }
        HusbandryMigration.start(plugin, repository);
        if (supports(loader, true)) HusbandryEstate.start(plugin, repository);
        else plugin.getLogger().warning("TLibs has no estate API; animal slots stay with dead characters.");
    }
}
