package net.tfminecraft.breedingbuddies.testsupport;

import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;

/** Real facade and registry with controllable activation generations. */
public final class TestCharacters implements AutoCloseable {
    public final CharacterIdentity provider = mock(CharacterIdentity.class);
    private final Plugin plugin;
    private final Map<UUID, CharacterSession> sessions = new HashMap<>();
    private long generation;

    public TestCharacters(Plugin plugin) {
        this.plugin = plugin;
        when(provider.currentSession(any())).thenAnswer(call -> {
            Player player = call.getArgument(0);
            return player == null ? Optional.empty() : Optional.ofNullable(sessions.get(player.getUniqueId()));
        });
        when(provider.isCurrent(any())).thenAnswer(call -> {
            CharacterSession session = call.getArgument(0);
            return session != null && session.equals(sessions.get(session.ref().ownerId()));
        });
        register();
    }

    public CharacterSession activate(Player player, UUID characterId) {
        CharacterSession token = new CharacterSession(new CharacterRef(player.getUniqueId(), characterId), ++generation);
        sessions.put(player.getUniqueId(), token);
        return token;
    }

    public CharacterSession activate(Player player, String characterId) {
        return activate(player, UUID.fromString(characterId));
    }

    public void set(Player player, CharacterSession session) {
        if (session == null) sessions.remove(player.getUniqueId());
        else sessions.put(player.getUniqueId(), session);
    }

    public void register() {
        Bukkit.getServicesManager().register(CharacterIdentity.class, provider, plugin, ServicePriority.Normal);
    }

    @Override public void close() {
        Bukkit.getServicesManager().unregister(CharacterIdentity.class, provider);
    }
}
