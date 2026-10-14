package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class HusbandryInspectListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        HusbandryMounts.enrollIfNeeded(living);
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(living.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }

        if (!wantsInspect(player, hand)) {
            return;
        }
        if (isBlockedItem(hand)) {
            return;
        }
        openInspect(player, living, stored.get(), event);
    }

    private static void openInspect(
            Player player,
            LivingEntity living,
            HusbandryAnimal animal,
            PlayerInteractEntityEvent event) {
        event.setCancelled(true);
        if (!canView(player, animal)) {
            player.sendMessage("§cThis is not your animal.");
            return;
        }
        HusbandryMounts.applyStats(living, animal);
        HusbandryInspectGui.open(player, living, animal);
    }

    private static boolean canView(Player player, HusbandryAnimal animal) {
        return HusbandryOwnershipService.isOwner(player, animal.uuid())
                || HusbandryOwnershipService.isStaff(player)
                || !HusbandryOwnershipService.hasAnyOwner(animal.uuid());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof HusbandryInspectHolder inspect)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory() || event.getRawSlot() != HusbandryInspectGui.REMOVE_SLOT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!inspect.isCurrent(player)) {
            player.closeInventory();
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.WRITABLE_BOOK) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(inspect.animalUuid());
        if (stored.isEmpty()) {
            player.closeInventory();
            return;
        }
        HusbandryOwnershipService.Result result = HusbandryOwnershipService.removeSelf(player, stored.get());
        player.closeInventory();
        if (result == HusbandryOwnershipService.Result.OK) {
            player.sendMessage("§aYou no longer own this animal.");
        } else {
            player.sendMessage("§cCould not remove ownership.");
        }
    }

    private static boolean wantsInspect(Player player, ItemStack hand) {
        boolean sneakEmpty = player.isSneaking() && (hand == null || hand.getType().isAir());
        boolean inspectItem = HusbandryItems.matches(hand, HusbandryConfig.inspectItem());
        return sneakEmpty || inspectItem;
    }

    private static boolean isBlockedItem(ItemStack hand) {
        if (hand == null || hand.getType().isAir()) {
            return false;
        }
        if (hand.getType() == Material.BUCKET) {
            return true;
        }
        return HusbandryItems.matches(hand, HusbandryConfig.feedItem())
                || HusbandryItems.matches(hand, HusbandryConfig.gloveItem())
                || HusbandryItems.matches(hand, HusbandryConfig.tameItem())
                || HusbandryItems.matches(hand, HusbandryConfig.coOwnItem())
                || HusbandryItems.matches(hand, HusbandryConfig.neuterItem());
    }
}
