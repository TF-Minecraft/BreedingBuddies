package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.plugin.Plugin;
import net.tfminecraft.tlibs.character.Characters;

/** Kept out of the old-API classloading path. Every pass uses the same archived plan. */
final class HusbandryMigration {
    private int quietMinutes;
    private String lastProblem;
    private HusbandryMigration() {}
    static void start(Plugin plugin, HusbandryRepository repository) {
        HusbandryMigration migration = new HusbandryMigration();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (migration.quietMinutes > 0) {
                migration.quietMinutes--;
                return;
            }
            if (migration.attempt(plugin, repository)) migration.quietMinutes = 59;
        }, 1, 20L * 60);
    }
    static void reconcile(Plugin plugin, HusbandryRepository repository) {
        new HusbandryMigration().attempt(plugin, repository);
    }
    private boolean attempt(Plugin plugin, HusbandryRepository repository) {
        try {
            if (!repository.migrateOwners(Characters::legacyHeirsReady, Characters::legacyHeir)) {
                warnChanged(plugin, "Animal ownership waits for reviewed heirs; legacy slots remain held.");
                return false;
            }
            int held = repository.heldOwners();
            if (held != 0) {
                plugin.getLogger().warning("Animal ownership holds " + held
                        + " legacy slots for staff; see husbandry_owner_plan in Data/husbandry.db.");
            }
            lastProblem = null;
            return true;
        } catch (RuntimeException ex) {
            warnChanged(plugin, "Animal ownership migration rolled back; retrying: " + ex.getMessage());
            return false;
        }
    }
    private void warnChanged(Plugin plugin, String message) {
        if (!message.equals(lastProblem)) plugin.getLogger().warning(message);
        lastProblem = message;
    }
}
