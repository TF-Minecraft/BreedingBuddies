package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HusbandryMountListener implements Listener {

    @EventHandler
    public void onMount(EntityMountEvent event) {
        Entity mount = event.getMount();
        if (!HusbandryMounts.isMount(mount)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (HusbandryEntities.repository() == null || HusbandryIdentity.session(player).isEmpty()) {
            event.setCancelled(true);
            player.sendMessage("§cYour character's animal records are not ready.");
            return;
        }
        if (mount instanceof LivingEntity living) {
            HusbandryAnimal animal = HusbandryMounts.enrollIfNeeded(living);
            if (animal != null) {
            HusbandryMounts.applyStats(living, animal);
            }
        }
        if (!HusbandryOwnershipService.hasAnyOwner(mount.getUniqueId())) {
            HusbandryClaimHint.remind(player, mount);
            return;
        }
        if (HusbandryOwnershipService.isOwner(player, mount.getUniqueId())) {
            rideCountsAsPet(player, (LivingEntity) mount);
            return;
        }
        if (HusbandryOwnershipService.isStaff(player)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cThis is not your animal.");
    }

    /** Time in the saddle is a mount's daily pet; the right-click that mounts it never reaches petting. */
    private static void rideCountsAsPet(Player player, LivingEntity living) {
        HusbandryAnimal animal = HusbandryEntities.lookup(living.getUniqueId()).orElse(null);
        long now = System.currentTimeMillis();
        if (animal == null || !HusbandryTrust.canPet(animal, now)) {
            return;
        }
        HusbandryTrust.show(player, living, animal, HusbandryTrust.reward(animal, HusbandryTrust.Gesture.PET, now));
        HusbandryEntities.repository().upsertAnimal(animal);
    }

    @EventHandler
    public void onCharacterEnd(net.tfminecraft.tlibs.character.CharacterSessionEndEvent event) {
        // The event's session is already noncurrent; clean up the outgoing body directly.
        Player player = event.getPlayer();
        if (!event.getSession().ref().ownerId().equals(player.getUniqueId())) return;
        Entity vehicle = player.getVehicle();
        if (vehicle != null && HusbandryMounts.isMount(vehicle)) player.leaveVehicle();
        org.bukkit.inventory.Inventory open = player.getOpenInventory().getTopInventory();
        if (open != null && open.getHolder() instanceof HusbandryInspectHolder holder
                && holder.belongsTo(event.getSession())) player.closeInventory();
        HusbandryClaimHint.forget(player.getUniqueId());
    }

    @EventHandler
    public void onCharacterStart(net.tfminecraft.tlibs.character.CharacterSessionStartEvent event) {
        if (!HusbandryIdentity.current(event.getSession(), event.getPlayer())) return;
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && HusbandryOwnershipService.hasAnyOwner(vehicle.getUniqueId())
                && !HusbandryOwnershipService.isOwner(event.getPlayer(), vehicle.getUniqueId())
                && !HusbandryOwnershipService.isStaff(event.getPlayer())) event.getPlayer().leaveVehicle();
    }

    /** Vanilla sitting, feeding and mount-inventory interactions cannot use an account's tame owner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.Tameable
                && HusbandryOwnershipService.hasAnyOwner(event.getRightClicked().getUniqueId())
                && !HusbandryOwnershipService.isOwner(event.getPlayer(), event.getRightClicked().getUniqueId())
                && !HusbandryOwnershipService.isStaff(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player
                && HusbandryOwnershipService.hasAnyOwner(event.getEntity().getUniqueId())
                && !HusbandryOwnershipService.isOwner(player, event.getEntity().getUniqueId())
                && !HusbandryOwnershipService.isStaff(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            // The event fires before the animal is tamed, so it cannot report itself as tamed yet.
            HusbandryClaimHint.send(player, event.getEntity(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HusbandryClaimHint.forget(event.getPlayer().getUniqueId());
    }
}
