package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class HusbandryDamageListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || HusbandryEntities.lookup(entity.getUniqueId()).isEmpty()) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (cancelAttack(entity, attacker(byEntity))) {
                event.setCancelled(true);
            }
            return;
        }
        if (!HusbandryConfig.damageEnvironment() && isEnvironment(event.getCause())) {
            event.setCancelled(true);
        }
    }

    private static boolean cancelAttack(Entity victim, Entity attacker) {
        if (attacker instanceof Player player) {
            boolean owner = HusbandryOwnershipService.isOwner(player, victim.getUniqueId());
            if (owner) {
                return !HusbandryConfig.damageOwner();
            }
            if (HusbandryOwnershipService.isStaff(player)) {
                return false;
            }
            return !HusbandryConfig.damageOtherPlayers();
        }
        if (attacker instanceof Mob) {
            return !HusbandryConfig.damageMobs();
        }
        return false;
    }

    private static Entity attacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Entity shooter) {
                return shooter;
            }
        }
        return damager;
    }

    private static boolean isEnvironment(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL, FIRE, FIRE_TICK, LAVA, DROWNING, CONTACT, CRAMMING,
                    SUFFOCATION, FREEZE, LIGHTNING, HOT_FLOOR -> true;
            default -> false;
        };
    }
}
