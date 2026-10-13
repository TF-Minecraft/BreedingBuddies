package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class AnimalIdentity implements Listener {
    private AnimalIdentity() {}
    public static Optional<CharacterSession> session(Player player) { return player == null ? Optional.empty() : Characters.currentSession(player).filter(s -> current(s, player)); }
    public static boolean current(CharacterSession session, Player player) { return player != null && session != null && session.ref().ownerId().equals(player.getUniqueId()) && Characters.isCurrent(session); }
    public static Optional<CharacterSession> session(UUID account) { return session(Bukkit.getPlayer(account)); }
    public static boolean supports(ClassLoader loader, boolean estates) {
        try {
            Class<?> api = Class.forName("net.tfminecraft.tlibs.character.Characters", false, loader);
            api.getMethod("legacyHeirsReady"); api.getMethod("legacyHeir", UUID.class);
            if (estates) for (String name : new String[]{"Estates", "EstateParticipant", "EstateDecision", "Outcome", "Holdings"}) Class.forName("net.tfminecraft.tlibs.character.estate." + name, false, loader);
            return true;
        } catch (ReflectiveOperationException | LinkageError failure) { return false; }
    }
    static void open(AnimalOwnershipStore store) {
        try {
            if (supports(AnimalIdentity.class.getClassLoader(), true)) store.guard = AnimalEstates::guard;
            if (supports(AnimalIdentity.class.getClassLoader(), false)) AnimalHeirs.open(store);
            else store.open(false, ignored -> Optional.empty());
            if (store.ready && !store.held.isEmpty()) Bukkit.getLogger().warning("[BreedingBuddies] Held animal records: " + store.held);
            if (!store.ready) Bukkit.getLogger().warning("[BreedingBuddies] Animal ownership held: " + store.blocked());
        } catch (Exception failure) { store.ready = false; Bukkit.getLogger().warning("[BreedingBuddies] Animal ownership held: " + failure.getMessage()); }
    }
    static void start(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new AnimalIdentity(), plugin);
        if (supports(AnimalIdentity.class.getClassLoader(), true)) AnimalEstates.start(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            AnimalOwnershipStore store = PluginData.ownership();
            if (store != null && !store.ready) { open(store); PluginData.rebuildAnimals(); }
        }, 2L, 20L * 60 * 60);
    }
    @EventHandler public void ended(CharacterSessionEndEvent event) {
        Player player = event.getPlayer();
        if (!event.getSession().ref().ownerId().equals(player.getUniqueId())) return;
        if (player.getVehicle() != null) player.leaveVehicle();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof AnimalInventoryHolder holder && event.getSession().equals(holder.session())) player.closeInventory();
    }
    @EventHandler public void started(CharacterSessionStartEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() != null && OwnershipManager.hasOwner(player.getVehicle().getUniqueId()) && !OwnershipManager.isOwner(player.getUniqueId(), player.getVehicle().getUniqueId())) player.leaveVehicle();
    }
}
